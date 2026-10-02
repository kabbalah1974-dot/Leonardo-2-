package it.leonardo.antivirus;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/** Impronta SHA-256 di un flusso di byte, leggendolo a pezzi e rispettando l'interruzione. */
public final class Hashing {

    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final int CHUNK = 64 * 1024;

    private Hashing() {
    }

    /**
     * Calcola l'impronta in esadecimale minuscolo. Se il thread viene interrotto
     * (l'utente preme Interrompi) si ferma subito, anche a metà di un file enorme.
     */
    public static String sha256Hex(InputStream in) throws IOException, InterruptedException {
        Objects.requireNonNull(in, "in");
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 manca nella piattaforma", e);
        }
        byte[] buffer = new byte[CHUNK];
        int n;
        while ((n = in.read(buffer)) != -1) {
            if (Thread.interrupted()) throw new InterruptedException();
            md.update(buffer, 0, n);
        }
        byte[] digest = md.digest();
        char[] out = new char[digest.length * 2];
        for (int i = 0; i < digest.length; i++) {
            int v = digest[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }

    /** Vero solo per 64 caratteri esadecimali. Serve a non mandare in rete testo strano. */
    public static boolean isSha256Hex(String s) {
        if (s == null || s.length() != 64) return false;
        for (int i = 0; i < 64; i++) {
            char c = s.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!ok) return false;
        }
        return true;
    }
}
