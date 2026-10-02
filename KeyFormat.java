package it.leonardo.antivirus;

/** Controllo di forma della chiave VirusTotal incollata dall'utente. */
public final class KeyFormat {

    private static final int MIN = 32;
    private static final int MAX = 128;

    private KeyFormat() {
    }

    /** Lettere e cifre, da 32 a 128 caratteri, senza spazi né a capo. Le chiavi vere sono 64 esadecimali. */
    public static boolean isPlausible(String key) {
        if (key == null || key.length() < MIN || key.length() > MAX) return false;
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
            if (!ok) return false;
        }
        return true;
    }
}
