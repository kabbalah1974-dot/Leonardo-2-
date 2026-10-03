package it.salvatore.ai;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** I file dei modelli sul tablet: dove stanno, come si scaricano e come si importano da Drive o dalla memoria. */
public final class ModelStore {

    private ModelStore() {}

    public interface Progress {
        /** done e total in byte; total può essere -1 se non si conosce. */
        void onProgress(long done, long total);
    }

    public static File dir(Context c) {
        File d = new File(c.getFilesDir(), "modelli");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    /** Solo il nome del file, senza percorsi (per sicurezza). */
    public static String safeName(String name) {
        String n = name == null ? "" : new File(name).getName().trim();
        return n;
    }

    public static File file(Context c, String name) {
        return new File(dir(c), safeName(name));
    }

    public static boolean isReady(Context c, String name) {
        if (safeName(name).isEmpty()) return false;
        File f = file(c, name);
        return f.isFile() && f.length() > 1_000_000L;
    }

    public static String mb(long bytes) {
        return Math.round(bytes / 1048576.0) + " MB";
    }

    public static boolean delete(Context c, String name) {
        boolean ok = true;
        File f = file(c, name);
        if (f.exists()) ok = f.delete();
        File part = new File(f.getPath() + ".part");
        if (part.exists()) ok = part.delete() && ok;
        return ok;
    }

    /** Scarica (e riprende, se interrotto) il file in "modelli". Si ferma se cancel.cancelled diventa true. */
    public static void download(Context c, String url, String name, Progress p, OpenAiClient.Cancel cancel)
            throws IOException {
        File dest = file(c, name);
        File part = new File(dest.getPath() + ".part");
        long have = part.isFile() ? part.length() : 0L;
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        if (cancel != null) cancel.setOnCancel(() -> {
            try {
                conn.disconnect();
            } catch (RuntimeException ignored) {
            }
        });
        try {
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(60000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Salvatore/1.0");
            if (have > 0) conn.setRequestProperty("Range", "bytes=" + have + "-");
            int code = conn.getResponseCode();
            if (code != 200 && code != 206) {
                throw new IOException("Il sito ha risposto con l'errore " + code + ".");
            }
            boolean append = code == 206 && have > 0;
            long len = conn.getContentLengthLong();
            long total = len < 0 ? -1 : (append ? have + len : len);
            long done = append ? have : 0L;
            byte[] buf = new byte[64 * 1024];
            long lastReport = 0;
            try (InputStream in = conn.getInputStream();
                 OutputStream out = new FileOutputStream(part, append)) {
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (cancel != null && cancel.cancelled) throw new IOException("Scaricamento interrotto.");
                    out.write(buf, 0, n);
                    done += n;
                    long now = System.currentTimeMillis();
                    if (p != null && now - lastReport > 500) {
                        lastReport = now;
                        p.onProgress(done, total);
                    }
                }
            }
            if (total > 0 && done < total) throw new IOException("Il file è arrivato incompleto. Riprova: riparte da dove era arrivato.");
            if (dest.exists() && !dest.delete()) throw new IOException("Non riesco a sostituire il vecchio file.");
            if (!part.renameTo(dest)) throw new IOException("Non riesco a salvare il file.");
            if (p != null) p.onProgress(done, total);
        } catch (IOException e) {
            if (cancel != null && cancel.cancelled) throw new IOException("Scaricamento interrotto. Riprendi quando vuoi: riparte da dove era arrivato.");
            throw e;
        } finally {
            if (cancel != null) cancel.setOnCancel(null);
            conn.disconnect();
        }
    }

    /** Nome visibile di un file scelto con il selettore di Android (Drive o memoria del tablet). */
    public static String displayName(Context c, Uri uri) {
        ContentResolver r = c.getContentResolver();
        try (Cursor cur = r.query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                String n = cur.getString(0);
                if (n != null) return n;
            }
        } catch (RuntimeException ignored) {
        }
        String last = uri.getLastPathSegment();
        return last == null ? "modello.litertlm" : last;
    }

    /** Copia nel tablet un file scelto con il selettore. Restituisce il nome con cui è stato salvato. */
    public static String importFrom(Context c, Uri uri, String name, Progress p, OpenAiClient.Cancel cancel)
            throws IOException {
        String safe = safeName(name);
        if (safe.isEmpty()) throw new IOException("Il file non ha un nome valido.");
        File dest = file(c, safe);
        File part = new File(dest.getPath() + ".part");
        long total = -1;
        try (android.content.res.AssetFileDescriptor fd = c.getContentResolver().openAssetFileDescriptor(uri, "r")) {
            if (fd != null) total = fd.getLength();
        } catch (IOException | RuntimeException ignored) {
        }
        try (InputStream in = c.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(part, false)) {
            if (in == null) throw new IOException("Non riesco ad aprire il file scelto.");
            byte[] buf = new byte[64 * 1024];
            long done = 0;
            long lastReport = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                if (cancel != null && cancel.cancelled) throw new IOException("Copia interrotta.");
                out.write(buf, 0, n);
                done += n;
                long now = System.currentTimeMillis();
                if (p != null && now - lastReport > 500) {
                    lastReport = now;
                    p.onProgress(done, total);
                }
            }
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            part.delete();
            throw e;
        }
        if (dest.exists() && !dest.delete()) throw new IOException("Non riesco a sostituire il vecchio file.");
        if (!part.renameTo(dest)) throw new IOException("Non riesco a salvare il file.");
        return safe;
    }
}
