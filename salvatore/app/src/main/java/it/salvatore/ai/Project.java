package it.salvatore.ai;

/** La scheda del progetto: un testo condiviso da tutti i cervelli, con un registro in fondo. */
public final class Project {

    private Project() {}

    public static final String LOG_HEADER = "## Registro";

    public static final String TEMPLATE =
            "# Progetto\n\nObiettivo: (scrivilo qui)\n\nDecisioni prese:\n- \n\n" + LOG_HEADER + "\n";

    /** Il pezzo di scheda da dare al cervello: se è lunga, inizio e fine. */
    public static String forPrompt(String doc, int max) {
        if (doc == null) return "";
        String d = doc.trim();
        if (d.length() <= max) return d;
        int head = max / 3;
        int tail = max - head;
        return d.substring(0, head) + "\n[...]\n" + d.substring(d.length() - tail);
    }

    /** Aggiunge una riga in fondo al registro (lo crea se manca). Le righe si aggiungono sempre in coda. */
    public static String appendLog(String doc, String line) {
        String d = doc == null ? "" : doc;
        if (d.isEmpty()) d = TEMPLATE;
        if (!d.contains(LOG_HEADER)) d = d + (d.endsWith("\n") ? "" : "\n") + "\n" + LOG_HEADER + "\n";
        if (!d.endsWith("\n")) d = d + "\n";
        return d + line.replace('\n', ' ').trim() + "\n";
    }

    /**
     * Unisce la scheda del tablet con quella su GitHub.
     * base = l'ultima versione già sincronizzata. Se il tablet ha solo aggiunto righe in fondo,
     * si prende la versione di GitHub e si aggiungono quelle righe. Se invece il tablet ha modificato
     * il testo in altri punti e anche GitHub è cambiato, si tengono tutte e due le versioni.
     */
    public static String merge(String base, String local, String remote) {
        String b = base == null ? "" : base;
        String l = local == null ? "" : local;
        String r = remote == null ? "" : remote;
        if (l.equals(r)) return r;
        if (l.equals(b)) return r;
        if (r.equals(b)) return l;
        if (l.startsWith(b)) {
            String tail = l.substring(b.length());
            while (tail.startsWith("\n")) tail = tail.substring(1);
            return r + (r.endsWith("\n") || r.isEmpty() ? "" : "\n") + tail;
        }
        return r + (r.endsWith("\n") ? "" : "\n") + "\n## Versione del tablet (da controllare)\n" + l;
    }

    private static String cut(String s, int max) {
        String t = s == null ? "" : s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    /** Una riga di registro: quando, chi ha risposto, domanda e risposta in breve. */
    public static String logLine(String when, String who, String question, String answer) {
        return "- " + when + " · " + cut(who, 40) + ": «" + cut(question, 100) + "» → " + cut(answer, 240);
    }
}
