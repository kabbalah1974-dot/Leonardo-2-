package it.leonardo.antivirus;

/** Una riga della cronologia: quando, quante app, quante segnalazioni e che punteggio uscì allora. */
public final class HistoryEntry {
    public final long whenMs;
    public final int apps;
    public final int high;
    public final int medium;
    public final int low;
    public final int score;

    public HistoryEntry(long whenMs, int apps, int high, int medium, int low, int score) {
        this.whenMs = whenMs;
        this.apps = apps;
        this.high = high;
        this.medium = medium;
        this.low = low;
        this.score = score;
    }
}
