package it.leonardo.antivirus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Tutte le regole di giudizio in un posto solo, senza Android: si possono provare senza tablet. */
public final class RiskPolicy {

    public static final int HIGH_FROM = 6;
    public static final int MEDIUM_FROM = 3;
    /** Sotto questo SDK un'app riceve tutti i permessi all'installazione, senza chiedere. */
    public static final int OLD_TARGET_SDK = 23;

    private RiskPolicy() {
    }

    public static Assessment assess(AppFacts f) {
        Objects.requireNonNull(f, "f");
        int score = 0;
        List<String> reasons = new ArrayList<>();
        boolean store = f.fromStore;

        if (!store) {
            score += 1;
            reasons.add("Non è stata installata da un negozio affidabile.");
        }
        if (f.accessibilityOn) {
            score += store ? 3 : 5;
            reasons.add("Ha il controllo dello schermo attivo (accessibilità): può leggere e toccare al posto tuo.");
        }
        if (f.deviceAdmin) {
            score += store ? 2 : 3;
            reasons.add("È amministratore del dispositivo e può essere difficile da disinstallare.");
        }
        if (f.notificationListener) {
            score += store ? 1 : 3;
            reasons.add("Legge le tue notifiche, compresi i codici di verifica.");
        }
        if (f.readsSms) {
            score += store ? 1 : 2;
            reasons.add("Può leggere i tuoi SMS.");
        }
        if (f.overlay && f.readsSms) {
            score += 3;
            reasons.add("Può disegnare sopra le altre app e leggere gli SMS: è la combinazione tipica dei virus che rubano i dati della banca.");
        } else if (f.overlay && !store) {
            score += 1;
            reasons.add("Può disegnare sopra le altre app.");
        }
        if (!store && f.canInstallApps) {
            score += 1;
            reasons.add("Può installare altre app.");
        }
        if (!store && f.allFilesAccess) {
            score += 1;
            reasons.add("Può leggere e modificare tutti i file del tablet.");
        }
        if (!store && f.targetSdk > 0 && f.targetSdk < OLD_TARGET_SDK) {
            score += 1;
            reasons.add("È costruita per versioni molto vecchie di Android e salta i controlli moderni sui permessi.");
        }
        if (!store && !f.hasLauncher) {
            score += 1;
            reasons.add("Non ha un'icona nel menu delle app: può restare nascosta.");
        }
        if (f.debuggable) {
            score += 1;
            reasons.add("È una versione di prova (debug), non una app normale.");
        }
        return new Assessment(score, reasons);
    }

    /** Da punteggio a gravità. Punteggio zero o negativo = nulla da segnalare. */
    public static Severity severityForScore(int score) {
        if (score >= HIGH_FROM) return Severity.HIGH;
        if (score >= MEDIUM_FROM) return Severity.MEDIUM;
        if (score >= 1) return Severity.LOW;
        return Severity.OK;
    }

    public static Severity severityForVirusTotal(int malicious, int suspicious) {
        // Un solo antivirus su decine che segnala è il quadro tipico di un falso allarme: basso.
        if (malicious >= 3) return Severity.HIGH;
        if (malicious == 2) return Severity.MEDIUM;
        if (malicious == 1 || suspicious >= 1) return Severity.LOW;
        return Severity.OK;
    }

    /** La più grave delle due. L'ordine dell'enum va dalla peggiore alla migliore. */
    public static Severity worst(Severity a, Severity b) {
        return a.ordinal() <= b.ordinal() ? a : b;
    }

    /** Punteggio del tablet da 0 a 100. Numeri negativi sono trattati come zero. */
    public static int deviceScore(int high, int medium, int low) {
        long penalty = 18L * Math.max(0, high) + 7L * Math.max(0, medium) + 2L * Math.max(0, low);
        return (int) Math.max(0L, 100L - penalty);
    }
}
