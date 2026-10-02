package it.leonardo.antivirus;

/** Risposta di VirusTotal a "cosa sai di questo file?". */
public final class VtResult {

    public enum Kind { FOUND, NOT_FOUND, BAD_KEY, RATE_LIMITED, QUOTA_EXCEEDED, FAILED }

    public final Kind kind;
    public final int malicious;
    public final int suspicious;
    public final int total;
    public final String message;

    private VtResult(Kind kind, int malicious, int suspicious, int total, String message) {
        this.kind = kind;
        this.malicious = malicious;
        this.suspicious = suspicious;
        this.total = total;
        this.message = message;
    }

    public static VtResult found(int malicious, int suspicious, int total) {
        return new VtResult(Kind.FOUND, malicious, suspicious, total, "");
    }

    public static VtResult of(Kind kind) {
        return new VtResult(kind, 0, 0, 0, "");
    }

    public static VtResult failed(String message) {
        return new VtResult(Kind.FAILED, 0, 0, 0, message);
    }

    /**
     * Un 429 può voler dire "troppo in fretta" (basta aspettare un minuto) oppure "quota giornaliera
     * finita" (inutile riprovare oggi). VirusTotal li distingue nel corpo della risposta.
     */
    public static VtResult forTooManyRequests(String body) {
        boolean quota = body != null && body.contains("QuotaExceededError");
        return of(quota ? Kind.QUOTA_EXCEEDED : Kind.RATE_LIMITED);
    }

    /**
     * Costruisce l'esito dalle statistiche di VirusTotal. Numeri negativi valgono zero.
     * Se nessun antivirus ha ancora analizzato il file (totale zero) NON è un "pulito":
     * è un'analisi non pronta, e va detto.
     */
    public static VtResult fromStats(int malicious, int suspicious, int harmless, int undetected) {
        int m = Math.max(0, malicious);
        int s = Math.max(0, suspicious);
        long total = (long) m + s + Math.max(0, harmless) + Math.max(0, undetected);
        if (total == 0) return failed("file noto ma analisi non ancora pronta");
        return found(m, s, (int) Math.min(total, Integer.MAX_VALUE));
    }
}
