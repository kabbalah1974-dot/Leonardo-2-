package it.salvatore.ai;

/** Toglie dalle risposte i "pensieri" tra <think> e </think>, anche se arrivano a pezzi. */
public final class ThinkFilter {

    private static final String OPEN = "<think>";
    private static final String CLOSE = "</think>";

    private boolean inThink = false;
    private boolean trimLead = false;
    private final StringBuilder pend = new StringBuilder();

    public String feed(String s) {
        if (s == null || s.isEmpty()) return "";
        pend.append(s);
        StringBuilder out = new StringBuilder();
        while (true) {
            if (!inThink) {
                int i = pend.indexOf(OPEN);
                if (i >= 0) {
                    out.append(pend, 0, i);
                    pend.delete(0, i + OPEN.length());
                    inThink = true;
                    continue;
                }
                int keep = partial(pend, OPEN);
                int n = pend.length() - keep;
                out.append(pend, 0, n);
                pend.delete(0, n);
                break;
            } else {
                int j = pend.indexOf(CLOSE);
                if (j >= 0) {
                    pend.delete(0, j + CLOSE.length());
                    inThink = false;
                    trimLead = true;
                    continue;
                }
                int keep = partial(pend, CLOSE);
                pend.delete(0, pend.length() - keep);
                break;
            }
        }
        String r = out.toString();
        if (trimLead && !r.isEmpty()) {
            int k = 0;
            while (k < r.length() && Character.isWhitespace(r.charAt(k))) k++;
            r = r.substring(k);
            if (!r.isEmpty()) trimLead = false;
        }
        return r;
    }

    /** Alla fine: restituisce quello che era rimasto in attesa (se non era dentro un pensiero). */
    public String finish() {
        String r = inThink ? "" : pend.toString();
        pend.setLength(0);
        return r;
    }

    private static int partial(StringBuilder sb, String tag) {
        int max = Math.min(sb.length(), tag.length() - 1);
        for (int k = max; k > 0; k--) {
            if (sb.substring(sb.length() - k).equals(tag.substring(0, k))) return k;
        }
        return 0;
    }
}
