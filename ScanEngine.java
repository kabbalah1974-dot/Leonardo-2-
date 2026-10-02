package it.leonardo.antivirus;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Coordina i controlli. Ce n'è uno solo per tutta l'app, quindi un controllo in corso sopravvive
 * a rotazione dello schermo e cambio di tema. Lavora in background e consegna all'interfaccia
 * una fotografia (UiState) a ogni passo.
 */
public final class ScanEngine {

    public interface Listener {
        /** Chiamato sempre sul thread principale. */
        void onState(UiState state);
    }

    private interface Task {
        void run() throws Exception;
    }

    private interface Mod {
        void apply(UiState state);
    }

    private static final String TAG = "Leonardo";
    /** Il piano gratuito di VirusTotal permette 4 richieste al minuto: una ogni 16 secondi è sicuro. */
    private static final long PAUSE_MS = 16_000L;
    private static final long RATE_LIMIT_WAIT_MS = 60_000L;
    private static final int MAX_ONLINE_APPS = 15;
    private static final int MAX_FILES = 20;
    private static final String HISTORY_PREFS = "storico";
    private static final String HISTORY_KEY = "scansioni";

    private static ScanEngine instance;

    public static synchronized ScanEngine get(Context context) {
        if (instance == null) instance = new ScanEngine(context.getApplicationContext());
        return instance;
    }

    private final Context context;
    private final KeyVault vault;
    private final VirusTotalClient vt = new VirusTotalClient();
    private final RateGate gate = new RateGate(PAUSE_MS, () -> System.nanoTime() / 1_000_000L, Thread::sleep);
    private final Handler main = new Handler(Looper.getMainLooper());
    /** Per le operazioni brevi (chiave, cronologia): non devono aspettare un controllo di minuti. */
    private final ExecutorService quick = Executors.newSingleThreadExecutor();
    private final ThreadLocal<Integer> runGeneration = new ThreadLocal<>();
    private volatile Listener listener;
    private Future<?> task;
    private int generation;
    private volatile int rateStrikes;
    private UiState state = new UiState();

    private ScanEngine(Context context) {
        this.context = context;
        this.vault = new KeyVault(context);
        quick.submit(this::loadStoredData);
    }

    // ---------- comandi dall'interfaccia ----------

    /** Collega l'interfaccia (o la scollega con null). Chi si collega riceve subito lo stato attuale. */
    public void setListener(Listener l) {
        listener = l;
        if (l != null) change(s -> { });
    }

    /** Scollega l'interfaccia solo se è ancora quella indicata (una nuova potrebbe essersi già collegata). */
    public void removeListener(Listener l) {
        if (listener == l) listener = null;
    }

    public synchronized boolean hasKey() {
        return state.hasKey;
    }

    /** Salva la chiave (o la cancella se vuota). Il lavoro sul Keystore avviene in background. */
    public void saveKey(final String rawKey) {
        final String key = rawKey == null ? "" : rawKey.trim();
        if (!key.isEmpty() && !KeyFormat.isPlausible(key)) {
            change(s -> {
                if (!s.busy) s.status = "Questa chiave non sembra valida: di solito sono 64 caratteri, senza spazi.";
            });
            return;
        }
        quick.submit(() -> {
            try {
                vault.save(key);
                final boolean has = vault.load() != null;
                change(s -> {
                    s.hasKey = has;
                    if (!s.busy) s.status = has ? "Chiave VirusTotal salvata." : "Chiave VirusTotal rimossa.";
                });
            } catch (Exception e) {
                Log.e(TAG, "salvataggio chiave", e);
                change(s -> {
                    if (!s.busy) s.status = "Non sono riuscito a salvare la chiave.";
                });
            }
        });
    }

    public void clearHistory() {
        quick.submit(() -> {
            context.getSharedPreferences(HISTORY_PREFS, Context.MODE_PRIVATE).edit().remove(HISTORY_KEY).apply();
            change(s -> s.history = Collections.emptyList());
        });
    }

    public void scanApps() {
        begin(s -> {
            s.appsDone = false;
            s.appFindings = Collections.emptyList();
            s.status = "Controllo le app installate…";
        }, this::runAppScan);
    }

    public void scanFiles(List<Uri> uris) {
        final List<Uri> copy = new ArrayList<>();
        for (Uri u : uris) {
            if (u != null) copy.add(u);
        }
        if (copy.isEmpty()) return;
        begin(s -> {
            s.fileFindings = Collections.emptyList();
            s.status = "Leggo i file…";
        }, () -> runFileScan(copy));
    }

    public synchronized void cancel() {
        if (!state.busy) return;
        generation++; // da ora il lavoro interrotto non può più scrivere sullo schermo
        if (task != null) task.cancel(true);
        // La connessione si chiude fuori dal thread principale: la rete lì non è ammessa.
        quick.submit(() -> {
            try {
                vt.abort();
            } catch (RuntimeException ignored) {
                // la richiesta finirà da sola entro i tempi massimi
            }
        });
        change(s -> {
            s.busy = false;
            s.status = "Controllo interrotto.";
        });
    }

    // ---------- avvio e stato ----------

    private synchronized void begin(Mod initial, final Task job) {
        if (state.busy) return;
        final int gen = ++generation;
        rateStrikes = 0;
        change(s -> {
            initial.apply(s);
            s.busy = true;
        });
        // Un esecutore nuovo per ogni controllo: uno interrotto ma ancora in coda non blocca il successivo.
        ExecutorService runner = Executors.newSingleThreadExecutor();
        task = runner.submit(() -> {
            runGeneration.set(gen);
            try {
                job.run();
            } catch (InterruptedException e) {
                // interrotto dall'utente: cancel() ha già aggiornato lo stato
            } catch (Throwable t) {
                fail(t);
            } finally {
                runGeneration.remove();
            }
        });
        runner.shutdown();
    }

    private synchronized void change(Mod mod) {
        Integer owner = runGeneration.get();
        if (owner != null && owner != generation) return; // lavoro già interrotto o superato
        UiState next = new UiState(state);
        mod.apply(next);
        state = next;
        final UiState snapshot = next;
        main.post(() -> {
            Listener l = listener;
            if (l != null) l.onState(snapshot);
        });
    }

    private void loadStoredData() {
        boolean stored = vault.hasStoredKey();
        final boolean readable = stored && vault.load() != null;
        final List<HistoryEntry> history = HistoryCodec.decode(
                context.getSharedPreferences(HISTORY_PREFS, Context.MODE_PRIVATE).getString(HISTORY_KEY, ""));
        final boolean broken = stored && !readable;
        change(s -> {
            s.hasKey = readable;
            if (s.history.isEmpty()) s.history = history;
            if (broken) {
                s.status = "La chiave VirusTotal salvata non è più leggibile: inseriscila di nuovo nelle Impostazioni.";
            }
        });
    }

    private void checkCancel() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
    }

    private void fail(Throwable e) {
        Log.e(TAG, "errore nel controllo", e);
        change(s -> {
            s.busy = false;
            s.status = "Qualcosa è andato storto. Riprova; se succede ancora, avvisa Leonardo.";
        });
    }

    /**
     * Chiude un controllo di app arrivato in fondo (anche fermato da chiave non valida o quota finita):
     * i risultati locali restano e finiscono nella cronologia. Se l'utente preme Interrompi, invece,
     * il controllo non è completo e non viene registrato.
     */
    private void finishApps(final String status) {
        change(s -> {
            s.busy = false;
            s.appsDone = true;
            s.status = status;
            int[] c = Reports.countBySeverity(s.appFindings);
            HistoryEntry entry = new HistoryEntry(System.currentTimeMillis(), s.appsChecked,
                    c[0], c[1], c[2], RiskPolicy.deviceScore(c[0], c[1], c[2]));
            s.history = HistoryCodec.prepend(entry, s.history);
        });
        final String encoded;
        synchronized (this) {
            encoded = HistoryCodec.encode(state.history);
        }
        quick.submit(() -> context.getSharedPreferences(HISTORY_PREFS, Context.MODE_PRIVATE).edit()
                .putString(HISTORY_KEY, encoded).apply());
    }

    private void stopFiles(final String status) {
        change(s -> {
            s.busy = false;
            s.status = status;
        });
    }

    // ---------- controllo delle app ----------

    private void runAppScan() throws Exception {
        final AppScanner.Result result = new AppScanner(context).scan();
        checkCancel();

        List<Finding> findings = new ArrayList<>();
        for (AppInfo a : result.apps) {
            if (a.score > 0) {
                findings.add(new Finding(a.packageName, a.label, a.packageName,
                        RiskPolicy.severityForScore(a.score), a.reasons));
            }
        }
        final List<Finding> first = Reports.sorted(findings);
        change(s -> {
            s.appsChecked = result.totalUserApps;
            s.appFindings = first;
            s.appsDone = true;
            s.status = "Analisi locale finita.";
        });

        String key = vault.load();
        final List<AppInfo> candidates = new ArrayList<>();
        int outsideStores = 0;
        for (AppInfo a : result.apps) {
            if (a.fromStore) continue;
            outsideStores++;
            if (candidates.size() < MAX_ONLINE_APPS) candidates.add(a);
        }
        if (key == null || candidates.isEmpty()) {
            finishApps(key == null
                    ? "Controllo finito. Per il controllo online aggiungi la chiave VirusTotal nelle Impostazioni."
                    : "Controllo finito.");
            return;
        }

        int checked = 0;
        for (int i = 0; i < candidates.size(); i++) {
            final AppInfo app = candidates.get(i);
            final String progress = "Controllo online " + (i + 1) + " di " + candidates.size() + ": " + app.label;
            change(s -> s.status = progress);

            String hash = hashFile(app.apkPath);
            checkCancel();
            if (hash == null) {
                updateApp(app.packageName,
                        VtResult.failed("non sono riuscito a leggere il file dell'app"));
                continue;
            }
            VtResult r = lookup(hash, key);
            checkCancel();
            if (r.kind == VtResult.Kind.BAD_KEY) {
                finishApps("La chiave VirusTotal non è valida. Controllala nelle Impostazioni.");
                return;
            }
            updateApp(app.packageName, r);
            String stop = stopMessage(r);
            if (stop != null) {
                finishApps(stop);
                return;
            }
            if (r.kind == VtResult.Kind.FOUND || r.kind == VtResult.Kind.NOT_FOUND) checked++;
        }
        String note = outsideStores > candidates.size()
                ? " Online controllate " + checked + " app su " + outsideStores
                + " installate fuori dai negozi (limite " + MAX_ONLINE_APPS + " per volta)."
                : "";
        finishApps("Controllo finito." + note);
    }

    private void updateApp(final String packageName, final VtResult r) {
        change(s -> {
            List<Finding> updated = new ArrayList<>();
            for (Finding f : s.appFindings) {
                updated.add(f.id.equals(packageName) ? Reports.withVirusTotal(f, r) : f);
            }
            s.appFindings = Reports.sorted(updated);
        });
    }

    // ---------- controllo dei file scelti dall'utente ----------

    private void runFileScan(List<Uri> all) throws Exception {
        List<Uri> uris = all.size() > MAX_FILES ? all.subList(0, MAX_FILES) : all;
        String key = vault.load();

        for (int i = 0; i < uris.size(); i++) {
            Uri uri = uris.get(i);
            final String rawName = rawName(uri);
            final String name = Sanitize.label(rawName);
            final String progress = "File " + (i + 1) + " di " + uris.size() + ": " + name;
            change(s -> s.status = progress);
            String id = "f" + i + "-" + name;

            String hash = hashUri(uri);
            checkCancel();
            if (hash == null) {
                addFile(new Finding(id, name, "", Severity.UNKNOWN,
                        Collections.singletonList("Non sono riuscito a leggere questo file.")));
                continue;
            }

            List<String> base = new ArrayList<>();
            if (Sanitize.isApkName(rawName)) {
                base.add("È un'app installabile (APK). Installala solo se sai da dove viene.");
            }
            base.add("Impronta SHA-256: " + hash.substring(0, 16) + "…");

            List<String> reasons = new ArrayList<>();
            Severity severity = Severity.UNKNOWN;
            VtResult vtResult = null;
            if (key == null) {
                reasons.add("Aggiungi la chiave VirusTotal nelle Impostazioni per il controllo online.");
            } else {
                VtResult r = lookup(hash, key);
                checkCancel();
                vtResult = r;
                switch (r.kind) {
                    case FOUND:
                        severity = Reports.severityFor(r);
                        reasons.add(Reports.vtLine(r));
                        break;
                    case NOT_FOUND:
                        reasons.add("VirusTotal non conosce questo file. Non vuol dire che sia sicuro: nessuno l'ha mai analizzato.");
                        break;
                    case BAD_KEY:
                        stopFiles("La chiave VirusTotal non è valida. Controllala nelle Impostazioni.");
                        return;
                    case QUOTA_EXCEEDED:
                        reasons.add("Quota di VirusTotal esaurita: riprova più tardi o domani.");
                        break;
                    case RATE_LIMITED:
                        reasons.add("VirusTotal ha chiesto una pausa. Riprova tra un minuto.");
                        break;
                    default:
                        reasons.add("Controllo online non riuscito: " + r.message + ".");
                        break;
                }
            }
            reasons.addAll(base);
            addFile(new Finding(id, name, "", severity, reasons));
            String stop = vtResult == null ? null : stopMessage(vtResult);
            if (stop != null) {
                final String message = stop;
                change(s -> {
                    s.busy = false;
                    s.fileFindings = Reports.sorted(s.fileFindings);
                    s.status = message;
                });
                return;
            }
        }

        final String extra = all.size() > MAX_FILES
                ? " Controllati i primi " + MAX_FILES + " file su " + all.size() + "."
                : "";
        change(s -> {
            s.busy = false;
            s.fileFindings = Reports.sorted(s.fileFindings);
            s.status = "Controllo finito." + extra;
        });
    }

    private void addFile(final Finding f) {
        change(s -> {
            List<Finding> next = new ArrayList<>(s.fileFindings);
            next.add(f);
            s.fileFindings = next;
        });
    }

    /** Nome del file come lo dichiara il provider, senza pulizia. Va pulito prima di mostrarlo. */
    private String rawName(Uri uri) {
        try (Cursor c = context.getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String name = c.getString(0);
                if (name != null) return name;
            }
        } catch (RuntimeException ignored) {
            // si usa il nome di riserva qui sotto
        }
        String last = uri.getLastPathSegment();
        return last != null ? last : "";
    }

    // ---------- parti in comune ----------

    /** Impronta di un file scelto dall'utente; null se non si riesce a leggerlo. Propaga l'interruzione. */
    private String hashUri(Uri uri) throws InterruptedException {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            return in == null ? null : Hashing.sha256Hex(in);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private String hashFile(String path) throws InterruptedException {
        if (path == null || path.isEmpty()) return null;
        try (InputStream in = new FileInputStream(new File(path))) {
            return Hashing.sha256Hex(in);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Dice se il controllo online va fermato del tutto: quota giornaliera finita, oppure due richieste
     * di fila respinte per troppe pause (inutile aspettare altri minuti su ogni app). Null = si prosegue.
     */
    private String stopMessage(VtResult r) {
        if (r.kind == VtResult.Kind.QUOTA_EXCEEDED) {
            return "Quota di VirusTotal esaurita: riprova più tardi o domani.";
        }
        if (r.kind == VtResult.Kind.RATE_LIMITED) {
            rateStrikes++;
            if (rateStrikes >= 2) {
                return "VirusTotal ha chiesto troppe pause: controllo online fermato. Riprova tra qualche minuto.";
            }
        } else {
            rateStrikes = 0;
        }
        return null;
    }

    /** Una richiesta a VirusTotal rispettando la distanza minima; con un 429 aspetta un minuto e riprova una volta. */
    private VtResult lookup(String hash, String key) throws InterruptedException {
        gate.awaitTurn();
        checkCancel();
        VtResult r = vt.lookup(hash, key);
        if (r.kind == VtResult.Kind.RATE_LIMITED || r.kind == VtResult.Kind.QUOTA_EXCEEDED) {
            change(s -> s.status = "VirusTotal chiede una pausa. Aspetto un minuto…");
            gate.noteBackoff();
            Thread.sleep(RATE_LIMIT_WAIT_MS);
            gate.awaitTurn();
            checkCancel();
            r = vt.lookup(hash, key);
        }
        return r;
    }
}
