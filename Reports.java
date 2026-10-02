package it.leonardo.antivirus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Trasforma i risultati in voci del rapporto. Nessun legame con Android: si prova senza tablet. */
public final class Reports {

    private Reports() {
    }

    /** Dal peggiore al migliore, poi in ordine alfabetico senza badare alle maiuscole. */
    public static List<Finding> sorted(List<Finding> list) {
        List<Finding> copy = new ArrayList<>(list);
        Collections.sort(copy, (a, b) -> {
            int bySeverity = Integer.compare(a.severity.ordinal(), b.severity.ordinal());
            if (bySeverity != 0) return bySeverity;
            int byTitle = a.title.toLowerCase(Locale.ROOT).compareTo(b.title.toLowerCase(Locale.ROOT));
            return byTitle != 0 ? byTitle : a.id.compareTo(b.id);
        });
        return copy;
    }

    /** Quante voci alte, medie e basse ci sono: [alte, medie, basse]. */
    public static int[] countBySeverity(List<Finding> list) {
        int[] c = new int[3];
        for (Finding f : list) {
            if (f.severity == Severity.HIGH) c[0]++;
            else if (f.severity == Severity.MEDIUM) c[1]++;
            else if (f.severity == Severity.LOW) c[2]++;
        }
        return c;
    }

    public static Severity severityFor(VtResult r) {
        return RiskPolicy.severityForVirusTotal(r.malicious, r.suspicious);
    }

    public static String vtLine(VtResult r) {
        if (r.malicious + r.suspicious == 0) {
            return "VirusTotal: nessuno dei " + r.total + " antivirus lo segnala (non è una garanzia).";
        }
        return "VirusTotal: " + r.malicious + " antivirus su " + r.total
                + " lo segnalano come pericoloso, " + r.suspicious + " come sospetto.";
    }

    /** Aggiunge a un'app l'esito di VirusTotal. Ogni esito lascia una traccia visibile all'utente. */
    public static Finding withVirusTotal(Finding f, VtResult r) {
        switch (r.kind) {
            case FOUND:
                return f.with(RiskPolicy.worst(severityFor(r), f.severity), vtLine(r));
            case NOT_FOUND:
                return f.with(f.severity,
                        "VirusTotal non conosce questo file di installazione (può essere un'app rara o con più parti).");
            case RATE_LIMITED:
                return f.with(f.severity, "VirusTotal ha chiesto troppe pause: controllo online non completato.");
            case QUOTA_EXCEEDED:
                return f.with(f.severity, "Quota di VirusTotal esaurita: controllo online non fatto.");
            case BAD_KEY:
                return f.with(f.severity, "La chiave VirusTotal non è stata accettata.");
            default:
                return f.with(f.severity, "Controllo online non riuscito: " + r.message + ".");
        }
    }
}
