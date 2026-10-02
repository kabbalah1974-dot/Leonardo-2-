package it.leonardo.antivirus;

import java.util.ArrayList;
import java.util.List;

/** Trasforma la cronologia in testo e viceversa. Le righe rovinate vengono saltate, mai un errore. */
public final class HistoryCodec {

    public static final int MAX_ENTRIES = 8;

    private HistoryCodec() {
    }

    public static String encode(List<HistoryEntry> entries) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (HistoryEntry e : entries) {
            if (count == MAX_ENTRIES) break;
            if (count > 0) sb.append(';');
            sb.append(e.whenMs).append(',').append(e.apps).append(',').append(e.high).append(',')
                    .append(e.medium).append(',').append(e.low).append(',').append(e.score);
            count++;
        }
        return sb.toString();
    }

    /** Legge sia il formato nuovo (6 campi, col punteggio) sia quello della versione 2 (5 campi). */
    public static List<HistoryEntry> decode(String raw) {
        List<HistoryEntry> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String item : raw.split(";")) {
            if (out.size() == MAX_ENTRIES) break;
            String[] p = item.split(",");
            if (p.length != 5 && p.length != 6) continue;
            try {
                long when = Long.parseLong(p[0].trim());
                int apps = Integer.parseInt(p[1].trim());
                int high = Integer.parseInt(p[2].trim());
                int medium = Integer.parseInt(p[3].trim());
                int low = Integer.parseInt(p[4].trim());
                if (apps < 0 || high < 0 || medium < 0 || low < 0) continue;
                int score = p.length == 6
                        ? Integer.parseInt(p[5].trim())
                        : RiskPolicy.deviceScore(high, medium, low);
                if (score < 0 || score > 100) continue;
                out.add(new HistoryEntry(when, apps, high, medium, low, score));
            } catch (NumberFormatException ignored) {
                // riga rovinata: la salto
            }
        }
        return out;
    }

    /** Mette la nuova voce in testa e tiene solo le più recenti. */
    public static List<HistoryEntry> prepend(HistoryEntry fresh, List<HistoryEntry> old) {
        List<HistoryEntry> out = new ArrayList<>();
        out.add(fresh);
        for (HistoryEntry e : old) {
            if (out.size() == MAX_ENTRIES) break;
            out.add(e);
        }
        return out;
    }
}
