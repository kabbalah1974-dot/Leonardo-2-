package it.salvatore.ai;

import java.util.regex.Pattern;

/** Ripulisce il testo "alla Markdown" (asterischi, cancelletti) per mostrarlo semplice. */
public final class Fmt {

    private Fmt() {}

    private static final Pattern ITALIC = Pattern.compile("(?<![*\\w])\\*(?!\\s)([^*\\n]+?)(?<!\\s)\\*(?![*\\w])");

    /**
     * Il motore a volte lascia passare i "pezzi di byte" di emoji e simboli (Ġ, Ċ, ǒ, Ł...), che sono lettere
     * dell'alfabeto latino esteso mai usate in italiano. Ġ = spazio, Ċ = a capo; gli altri si tolgono.
     */
    public static String fixBytes(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder b = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u0100' && c <= '\u024F') {
                if (b == null) {
                    b = new StringBuilder(s.length());
                    b.append(s, 0, i);
                }
                if (c == '\u0120') b.append(' ');
                else if (c == '\u010A') b.append('\n');
                else if (c == '\u0109') b.append('\t');
            } else if (b != null) {
                b.append(c);
            }
        }
        return b == null ? s : b.toString();
    }

    public static String plain(String s) {
        if (s == null || s.isEmpty()) return "";
        java.util.List<String> out = new java.util.ArrayList<>();
        String[] lines = s.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            String t = l.trim();
            if (t.startsWith("```")) { // righe di apertura/chiusura del blocco di codice: si tolgono
                continue;
            }
            if (t.startsWith("* ") || t.startsWith("- ")) {
                int ind = l.indexOf(t.charAt(0));
                l = l.substring(0, ind) + "• " + t.substring(2);
            } else if (t.startsWith("#")) {
                int k = 0;
                while (k < t.length() && t.charAt(k) == '#') k++;
                l = t.substring(k).trim();
            }
            l = l.replace("**", "").replace("__", "").replace("`", "");
            l = ITALIC.matcher(l).replaceAll("$1");
            out.add(l);
        }
        return String.join("\n", out);
    }
}
