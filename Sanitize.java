package it.leonardo.antivirus;

import java.util.Locale;

/** Pulisce i nomi che arrivano da fuori (file e app) prima di mostrarli: sono testo non fidato. */
public final class Sanitize {

    public static final int MAX_CODE_POINTS = 80;
    public static final String EMPTY = "(senza nome)";

    private Sanitize() {
    }

    /** Il testo senza caratteri invisibili o di controllo e con gli a capo trasformati in spazi. Mai null. */
    public static String clean(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); ) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '\n' || cp == '\r' || cp == '\t') cp = ' ';
            if (!isHidden(cp)) sb.appendCodePoint(cp);
        }
        return sb.toString().trim();
    }

    /**
     * Da mostrare all'utente: pulito e accorciato a 80 caratteri (senza spezzare le emoji).
     * Mai null, mai vuoto.
     */
    public static String label(String raw) {
        String out = clean(raw);
        if (out.isEmpty()) return EMPTY;
        if (out.codePointCount(0, out.length()) <= MAX_CODE_POINTS) return out;
        int end = out.offsetByCodePoints(0, MAX_CODE_POINTS);
        return out.substring(0, end) + "…";
    }

    /** Vero se il nome (originale, non accorciato) finisce in .apk, ignorando maiuscole e caratteri nascosti. */
    public static boolean isApkName(String raw) {
        return clean(raw).toLowerCase(Locale.ROOT).endsWith(".apk");
    }

    private static boolean isHidden(int cp) {
        switch (Character.getType(cp)) {
            case Character.CONTROL:
            case Character.FORMAT:             // direzione del testo, larghezza zero, tag
            case Character.LINE_SEPARATOR:
            case Character.PARAGRAPH_SEPARATOR:
            case Character.PRIVATE_USE:
            case Character.SURROGATE:
            case Character.UNASSIGNED:
                return true;
            default:
                break;
        }
        if (cp == 0x034F || cp == 0x115F || cp == 0x1160 || cp == 0x3164 || cp == 0xFFA0) return true; // riempitivi
        if (cp >= 0xFE00 && cp <= 0xFE0F) return true;     // selettori di variante
        return cp >= 0xE0100 && cp <= 0xE01EF;
    }
}
