package it.leonardo.antivirus;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Prove della logica senza Android. Si lanciano con: java it.leonardo.antivirus.LogicTests */
final class LogicTests {

    private static int failed;
    private static int passed;

    private static void check(String name, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FALLITA: " + name);
        }
    }

    private static AppFacts.Builder calm() {
        return new AppFacts.Builder();
    }

    public static void main(String[] args) throws Exception {
        // --- giudizio sulle app ---
        Assessment clean = RiskPolicy.assess(calm().build());
        check("app tranquilla da negozio: punteggio 0", clean.score == 0);
        check("app tranquilla: nessun motivo", clean.reasons.isEmpty());
        check("app tranquilla: gravita OK", clean.severity() == Severity.OK);

        Assessment sideloaded = RiskPolicy.assess(calm().fromStore(false).build());
        check("fuori negozio: punteggio 1", sideloaded.score == 1);
        check("fuori negozio: gravita bassa", sideloaded.severity() == Severity.LOW);

        Assessment spy = RiskPolicy.assess(calm().fromStore(false).accessibilityOn(true).build());
        check("accessibilita fuori negozio: 1+5 = 6", spy.score == 6);
        check("accessibilita fuori negozio: gravita alta", spy.severity() == Severity.HIGH);

        Assessment storeAccess = RiskPolicy.assess(calm().accessibilityOn(true).build());
        check("accessibilita da negozio: 3, media", storeAccess.score == 3 && storeAccess.severity() == Severity.MEDIUM);

        Assessment banker = RiskPolicy.assess(calm().fromStore(false).readsSms(true).overlay(true).build());
        check("sms + sovrapposizione fuori negozio: 1+2+3 = 6", banker.score == 6);

        Assessment overlayOnlyStore = RiskPolicy.assess(calm().overlay(true).build());
        check("solo sovrapposizione da negozio: 0", overlayOnlyStore.score == 0);

        Assessment old = RiskPolicy.assess(calm().fromStore(false).targetSdk(22).build());
        check("SDK vecchio fuori negozio: 2", old.score == 2);
        Assessment unknownSdk = RiskPolicy.assess(calm().fromStore(false).targetSdk(0).build());
        check("SDK sconosciuto (0) non conta come vecchio", unknownSdk.score == 1);

        Assessment hidden = RiskPolicy.assess(calm().fromStore(false).hasLauncher(false).build());
        check("senza icona fuori negozio: 2", hidden.score == 2);
        Assessment hiddenStore = RiskPolicy.assess(calm().hasLauncher(false).build());
        check("senza icona ma da negozio: 0", hiddenStore.score == 0);

        Assessment everything = RiskPolicy.assess(calm().fromStore(false).accessibilityOn(true).deviceAdmin(true)
                .notificationListener(true).readsSms(true).overlay(true).canInstallApps(true)
                .allFilesAccess(true).debuggable(true).hasLauncher(false).targetSdk(21).build());
        check("tutto insieme: gravita alta", everything.severity() == Severity.HIGH);
        check("tutto insieme: un motivo per ogni segnale", everything.reasons.size() == 11);
        try {
            everything.reasons.add("x");
            check("motivi non modificabili", false);
        } catch (UnsupportedOperationException expected) {
            check("motivi non modificabili", true);
        }
        try {
            RiskPolicy.assess(null);
            check("assess(null) rifiutato", false);
        } catch (NullPointerException expected) {
            check("assess(null) rifiutato", true);
        }

        // --- soglie e confronti ---
        check("soglia 0 = OK", RiskPolicy.severityForScore(0) == Severity.OK);
        check("negativo = OK", RiskPolicy.severityForScore(-5) == Severity.OK);
        check("soglia 1 = basso", RiskPolicy.severityForScore(1) == Severity.LOW);
        check("soglia 2 = basso", RiskPolicy.severityForScore(2) == Severity.LOW);
        check("soglia 3 = medio", RiskPolicy.severityForScore(3) == Severity.MEDIUM);
        check("soglia 5 = medio", RiskPolicy.severityForScore(5) == Severity.MEDIUM);
        check("soglia 6 = alto", RiskPolicy.severityForScore(6) == Severity.HIGH);
        check("enorme = alto", RiskPolicy.severityForScore(Integer.MAX_VALUE) == Severity.HIGH);

        check("VT 0/0 = pulito", RiskPolicy.severityForVirusTotal(0, 0) == Severity.OK);
        check("VT 0/1 = basso", RiskPolicy.severityForVirusTotal(0, 1) == Severity.LOW);
        check("VT 1/0 = basso", RiskPolicy.severityForVirusTotal(1, 0) == Severity.LOW);
        check("VT 2/0 = medio", RiskPolicy.severityForVirusTotal(2, 0) == Severity.MEDIUM);
        check("VT 3/0 = alto", RiskPolicy.severityForVirusTotal(3, 0) == Severity.HIGH);

        check("worst: alto batte medio", RiskPolicy.worst(Severity.MEDIUM, Severity.HIGH) == Severity.HIGH);
        check("worst: pulito non attenua alto", RiskPolicy.worst(Severity.HIGH, Severity.OK) == Severity.HIGH);
        check("worst: simmetrico", RiskPolicy.worst(Severity.LOW, Severity.UNKNOWN) == Severity.LOW);

        // --- punteggio del tablet ---
        check("tablet pulito = 100", RiskPolicy.deviceScore(0, 0, 0) == 100);
        check("un alto = 82", RiskPolicy.deviceScore(1, 0, 0) == 82);
        check("misto = 100-18-14-6", RiskPolicy.deviceScore(1, 2, 3) == 62);
        check("mai sotto zero", RiskPolicy.deviceScore(50, 50, 50) == 0);
        check("input negativi trattati come zero", RiskPolicy.deviceScore(-4, -1, -9) == 100);
        check("nessun overflow", RiskPolicy.deviceScore(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE) == 0);

        // --- impronta SHA-256 ---
        check("sha256 di 'abc'", Hashing.sha256Hex(new ByteArrayInputStream("abc".getBytes("UTF-8")))
                .equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"));
        check("sha256 del vuoto", Hashing.sha256Hex(new ByteArrayInputStream(new byte[0]))
                .equals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"));
        byte[] big = new byte[3 * 1024 * 1024 + 17];
        for (int i = 0; i < big.length; i++) big[i] = (byte) (i * 31);
        byte[] ref = MessageDigest.getInstance("SHA-256").digest(big);
        StringBuilder expected = new StringBuilder();
        for (byte b : ref) expected.append(String.format("%02x", b));
        check("sha256 di 3 MB uguale al riferimento", Hashing.sha256Hex(new ByteArrayInputStream(big)).equals(expected.toString()));
        try {
            Hashing.sha256Hex(null);
            check("sha256(null) rifiutato", false);
        } catch (NullPointerException expectedNpe) {
            check("sha256(null) rifiutato", true);
        }
        check("errore di lettura propagato", failsWithIo());
        check("interruzione rispettata", stopsWhenInterrupted());

        // --- controllo del formato dell'impronta ---
        String good = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        check("impronta valida", Hashing.isSha256Hex(good));
        check("impronta maiuscola valida", Hashing.isSha256Hex(good.toUpperCase()));
        check("null non valido", !Hashing.isSha256Hex(null));
        check("vuota non valida", !Hashing.isSha256Hex(""));
        check("troppo corta non valida", !Hashing.isSha256Hex(good.substring(1)));
        check("troppo lunga non valida", !Hashing.isSha256Hex(good + "0"));
        check("carattere strano non valido", !Hashing.isSha256Hex(good.substring(0, 63) + "g"));
        check("tentativo di iniezione non valido", !Hashing.isSha256Hex("../../" + good.substring(6)));

        signalTests();
        virusTotalTests();
        reportTests();
        rateGateTests();
        historyTests();
        keyTests();
        sanitizeTests();

        System.out.println(passed + " prove riuscite, " + failed + " fallite.");
        if (failed > 0) System.exit(1);
    }

    private static void signalTests() {
        check("amministratore da negozio: 2", RiskPolicy.assess(calm().deviceAdmin(true).build()).score == 2);
        check("amministratore fuori negozio: 1+3", RiskPolicy.assess(calm().fromStore(false).deviceAdmin(true).build()).score == 4);
        check("notifiche da negozio: 1", RiskPolicy.assess(calm().notificationListener(true).build()).score == 1);
        check("notifiche fuori negozio: 1+3", RiskPolicy.assess(calm().fromStore(false).notificationListener(true).build()).score == 4);
        check("sms da negozio: 1", RiskPolicy.assess(calm().readsSms(true).build()).score == 1);
        check("sms fuori negozio: 1+2", RiskPolicy.assess(calm().fromStore(false).readsSms(true).build()).score == 3);
        check("sms+overlay da negozio: 1+3", RiskPolicy.assess(calm().readsSms(true).overlay(true).build()).score == 4);
        check("installa app da negozio: 0", RiskPolicy.assess(calm().canInstallApps(true).build()).score == 0);
        check("installa app fuori negozio: 2", RiskPolicy.assess(calm().fromStore(false).canInstallApps(true).build()).score == 2);
        check("tutti i file da negozio: 0", RiskPolicy.assess(calm().allFilesAccess(true).build()).score == 0);
        check("tutti i file fuori negozio: 2", RiskPolicy.assess(calm().fromStore(false).allFilesAccess(true).build()).score == 2);
        check("debug da negozio: 1", RiskPolicy.assess(calm().debuggable(true).build()).score == 1);
        Assessment everything = RiskPolicy.assess(calm().fromStore(false).accessibilityOn(true).deviceAdmin(true)
                .notificationListener(true).readsSms(true).overlay(true).canInstallApps(true)
                .allFilesAccess(true).debuggable(true).hasLauncher(false).targetSdk(21).build());
        // 1 +5 +3 +3 +2 +3 +1 +1 +1 +1 +1
        check("tutto insieme: punteggio 22", everything.score == 22);
    }

    private static void virusTotalTests() {
        check("fromStats tutto zero = analisi non pronta (mai pulito)", VtResult.fromStats(0, 0, 0, 0).kind == VtResult.Kind.FAILED);
        check("fromStats negativi = non pronta", VtResult.fromStats(-3, -1, -2, -9).kind == VtResult.Kind.FAILED);
        VtResult ok = VtResult.fromStats(0, 0, 60, 10);
        check("fromStats pulito: trovato, totale 70", ok.kind == VtResult.Kind.FOUND && ok.total == 70);
        VtResult bad = VtResult.fromStats(5, 2, 40, 10);
        check("fromStats cattivo: totale 57", bad.kind == VtResult.Kind.FOUND && bad.total == 57 && bad.malicious == 5);
        check("fromStats senza overflow", VtResult.fromStats(Integer.MAX_VALUE, Integer.MAX_VALUE, 0, 0).total == Integer.MAX_VALUE);
        check("1 solo allarme = basso (falso allarme probabile)", RiskPolicy.severityForVirusTotal(1, 0) == Severity.LOW);
        check("2 allarmi = medio", RiskPolicy.severityForVirusTotal(2, 0) == Severity.MEDIUM);
    }

    private static Finding finding(String id, String title, Severity s) {
        return new Finding(id, title, "", s, new ArrayList<String>());
    }

    private static void reportTests() {
        List<Finding> list = new ArrayList<>(Arrays.asList(
                finding("1", "zeta", Severity.LOW), finding("2", "Alfa", Severity.HIGH),
                finding("3", "beta", Severity.HIGH), finding("4", "mela", Severity.MEDIUM),
                finding("5", "Beta", Severity.HIGH)));
        List<Finding> sorted = Reports.sorted(list);
        check("ordine: alti prima", sorted.get(0).severity == Severity.HIGH && sorted.get(3).severity == Severity.MEDIUM);
        check("ordine: alfabetico senza maiuscole", sorted.get(0).title.equals("Alfa"));
        check("ordine: parita di titolo decisa dall'id", sorted.get(1).id.equals("3") && sorted.get(2).id.equals("5"));
        check("l'originale non viene toccato", list.get(0).id.equals("1"));
        check("lista vuota", Reports.sorted(new ArrayList<Finding>()).isEmpty());

        int[] c = Reports.countBySeverity(sorted);
        check("conteggi 3 alti, 1 medio, 1 basso", c[0] == 3 && c[1] == 1 && c[2] == 1);

        Finding base = finding("x", "App", Severity.MEDIUM);
        Finding found = Reports.withVirusTotal(base, VtResult.found(4, 0, 70));
        check("VT cattivo alza a alto", found.severity == Severity.HIGH);
        Finding clean = Reports.withVirusTotal(base, VtResult.found(0, 0, 70));
        check("VT pulito non abbassa la gravita", clean.severity == Severity.MEDIUM);
        check("VT pulito dice che non e una garanzia", clean.reasons.get(0).contains("non è una garanzia"));
        for (VtResult.Kind k : VtResult.Kind.values()) {
            VtResult r = k == VtResult.Kind.FOUND ? VtResult.found(0, 0, 1)
                    : k == VtResult.Kind.FAILED ? VtResult.failed("x") : VtResult.of(k);
            check("ogni esito lascia una traccia: " + k, Reports.withVirusTotal(base, r).reasons.size() == 1);
        }
        check("vtLine con segnalazioni", Reports.vtLine(VtResult.found(2, 1, 60)).contains("2 antivirus su 60"));
        Finding twice = Reports.withVirusTotal(found, VtResult.failed("x"));
        check("with: il motivo nuovo va in cima", twice.reasons.size() == 2 && twice.reasons.get(0).contains("non riuscito"));
    }

    private static final class FakeTime implements RateGate.Clock, RateGate.Sleeper {
        long now = 1000;
        long slept;
        @Override public long nowMs() { return now; }
        @Override public void sleep(long ms) { slept += ms; now += ms; }
    }

    private static void rateGateTests() throws Exception {
        FakeTime t = new FakeTime();
        RateGate gate = new RateGate(16_000, t, t);
        gate.awaitTurn();
        check("prima richiesta: nessuna attesa", t.slept == 0);
        gate.awaitTurn();
        check("seconda subito dopo: attende 16 s", t.slept == 16_000);
        t.now += 10_000;
        gate.awaitTurn();
        check("dopo 10 s attende solo 6 s", t.slept == 22_000);
        t.now += 60_000;
        gate.awaitTurn();
        check("dopo un minuto non attende", t.slept == 22_000);
        gate.noteBackoff();
        gate.awaitTurn();
        check("dopo un 429 la distanza riparte da ora", t.slept == 38_000);
        try {
            new RateGate(-1, t, t);
            check("distanza negativa rifiutata", false);
        } catch (IllegalArgumentException expected) {
            check("distanza negativa rifiutata", true);
        }
        RateGate zero = new RateGate(0, t, t);
        zero.awaitTurn();
        zero.awaitTurn();
        check("distanza zero: mai attesa", t.slept == 38_000);
    }

    private static void historyTests() {
        List<HistoryEntry> in = new ArrayList<>();
        for (int i = 0; i < 12; i++) in.add(new HistoryEntry(1000L + i, 50 + i, i, 2, 3, 70 - i));
        String text = HistoryCodec.encode(in);
        List<HistoryEntry> back = HistoryCodec.decode(text);
        check("cronologia: tiene solo 8 voci", back.size() == HistoryCodec.MAX_ENTRIES);
        check("cronologia: giro completo", back.get(0).whenMs == 1000L && back.get(0).score == 70 && back.get(7).apps == 57);
        check("cronologia: null e vuoto", HistoryCodec.decode(null).isEmpty() && HistoryCodec.decode("").isEmpty());
        List<HistoryEntry> v2 = HistoryCodec.decode("1700000000000,40,1,2,3");
        check("cronologia: formato vecchio, punteggio ricalcolato", v2.size() == 1 && v2.get(0).score == RiskPolicy.deviceScore(1, 2, 3));
        List<HistoryEntry> dirty = HistoryCodec.decode("abc;1,2,3;1,2,x,4,5,6;1,2,3,4,5,150;5,-1,0,0,0,10;1700,10,0,0,1,98");
        check("cronologia: righe rovinate saltate, quella buona resta", dirty.size() == 1 && dirty.get(0).score == 98);
        List<HistoryEntry> up = HistoryCodec.prepend(new HistoryEntry(1, 1, 0, 0, 0, 100), back);
        check("cronologia: nuova voce in testa, sempre 8", up.size() == 8 && up.get(0).whenMs == 1);
    }

    private static void keyTests() {
        String key = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        check("chiave normale valida", KeyFormat.isPlausible(key));
        check("chiave null non valida", !KeyFormat.isPlausible(null));
        check("chiave vuota non valida", !KeyFormat.isPlausible(""));
        check("chiave troppo corta non valida", !KeyFormat.isPlausible(key.substring(0, 31)));
        check("chiave troppo lunga non valida", !KeyFormat.isPlausible(key + key + "0"));
        check("chiave con spazio non valida", !KeyFormat.isPlausible(key.substring(0, 32) + " " + key.substring(33)));
        check("chiave con a capo non valida", !KeyFormat.isPlausible(key.substring(0, 32) + "\n" + key.substring(33)));
        check("chiave con simboli non valida", !KeyFormat.isPlausible(key.substring(0, 63) + "-"));
    }

    private static void sanitizeTests() {
        check("etichetta normale invariata", Sanitize.label("Google Play").equals("Google Play"));
        check("null diventa segnaposto", Sanitize.label(null).equals(Sanitize.EMPTY));
        check("vuoto diventa segnaposto", Sanitize.label("").equals(Sanitize.EMPTY));
        check("solo spazi diventa segnaposto", Sanitize.label("   ").equals(Sanitize.EMPTY));
        check("inversione bidi tolta", Sanitize.label("fattura\u202Efdp.apk").equals("fatturafdp.apk"));
        check("isolamenti bidi tolti", Sanitize.label("a\u2066b\u2069c").equals("abc"));
        check("a capo diventano spazi", Sanitize.label("uno\ndue\r\ntre").equals("uno due  tre"));
        check("caratteri di controllo tolti", Sanitize.label("a\u0000b\u0007c").equals("abc"));
        check("larghezza zero tolta", Sanitize.label("pay\u200Bpal").equals("paypal"));
        check("trattino morbido tolto", Sanitize.label("pay\u00ADpal").equals("paypal"));
        check("operatore invisibile tolto", Sanitize.label("a\u2062b").equals("ab"));
        check("separatore di riga tolto", Sanitize.label("a\u2028b").equals("ab"));
        check("riempitivo Hangul tolto", Sanitize.label("a\u3164b").equals("ab"));
        check("selettore di variante tolto", Sanitize.label("a\uFE0Fb").equals("ab"));
        check("carattere tag tolto", Sanitize.label("a" + new String(Character.toChars(0xE0041)) + "b").equals("ab"));
        check("APK con nome lungo riconosciuto", Sanitize.isApkName("q".repeat(90) + ".apk"));
        check("APK con maiuscole", Sanitize.isApkName("Gioco.APK"));
        check("APK con carattere nascosto in mezzo", Sanitize.isApkName("gioco.a\u200Bpk"));
        check("APK con direzione invertita riconosciuto", Sanitize.isApkName("fattura\u202Efdp.apk"));
        check("PDF non e APK", !Sanitize.isApkName("fattura.pdf"));
        check("null non e APK", !Sanitize.isApkName(null));
        String longName = "x".repeat(500);
        String cut = Sanitize.label(longName);
        check("nome lungo accorciato a 80 + puntini", cut.length() == Sanitize.MAX_CODE_POINTS + 1 && cut.endsWith("…"));
        check("nome di 80 esatti non accorciato", Sanitize.label("y".repeat(80)).equals("y".repeat(80)));
        String emoji = "😀".repeat(100);
        String cutEmoji = Sanitize.label(emoji);
        check("emoji non spezzate", cutEmoji.codePointCount(0, cutEmoji.length()) == Sanitize.MAX_CODE_POINTS + 1);
        check("429 al minuto", VtResult.forTooManyRequests("{\"error\":{\"code\":\"TooManyRequestsError\"}}").kind == VtResult.Kind.RATE_LIMITED);
        check("429 di quota", VtResult.forTooManyRequests("{\"error\":{\"code\":\"QuotaExceededError\"}}").kind == VtResult.Kind.QUOTA_EXCEEDED);
        check("429 senza corpo = al minuto", VtResult.forTooManyRequests(null).kind == VtResult.Kind.RATE_LIMITED);
        check("429 con corpo vuoto = al minuto", VtResult.forTooManyRequests("").kind == VtResult.Kind.RATE_LIMITED);
    }

    private static boolean failsWithIo() {
        try {
            Hashing.sha256Hex(new java.io.InputStream() {
                @Override
                public int read() throws IOException {
                    throw new IOException("disco rotto");
                }
            });
            return false;
        } catch (IOException expected) {
            return true;
        } catch (InterruptedException other) {
            return false;
        }
    }

    private static boolean stopsWhenInterrupted() throws InterruptedException {
        final AtomicReference<Boolean> stopped = new AtomicReference<>(false);
        Thread t = new Thread(() -> {
            Thread.currentThread().interrupt();
            try {
                Hashing.sha256Hex(new ByteArrayInputStream(new byte[1024 * 1024]));
            } catch (InterruptedException expected) {
                stopped.set(true);
            } catch (IOException ignored) {
                // non deve succedere
            }
        });
        t.start();
        t.join(5000);
        return stopped.get();
    }
}
