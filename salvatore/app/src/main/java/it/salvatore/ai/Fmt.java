package it.salvatore.ai;

import java.util.regex.Pattern;

/** Ripulisce il testo "alla Markdown" (asterischi, cancelletti) per mostrarlo semplice. */
public final class Fmt {

    private Fmt() {}

    private static final Pattern ITALIC = Pattern.compile("(?<![*\\w])\\*(?!\\s)([^*\\n]+?)(?<!\\s)\\*(?![*\\w])");

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
