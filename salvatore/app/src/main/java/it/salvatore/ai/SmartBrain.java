package it.salvatore.ai;

import java.util.List;

/** Prova il cervello scelto; se non risponde e c'è un cervello di riserva (dentro il tablet), passa a quello. */
public final class SmartBrain implements Pipeline.Llm {

    public interface Note {
        void onNote(String text);
    }

    private final Model.Brain primary;
    private final Model.Brain fallback;
    private final OpenAiClient.Cancel cancel;
    private final Note note;
    private boolean failedOver = false;

    public SmartBrain(Model.Brain primary, Model.Brain fallback, OpenAiClient.Cancel cancel, Note note) {
        this.primary = primary;
        this.fallback = fallback;
        this.cancel = cancel;
        this.note = note;
    }

    @Override
    public String chat(List<Model.Msg> messages, OpenAiClient.Sink sink) throws OpenAiClient.ChatException {
        if (failedOver) return OpenAiClient.chat(fallback, messages, sink, cancel);
        final boolean[] got = {false};
        OpenAiClient.Sink wrapped = d -> {
            got[0] = true;
            if (sink != null) sink.onDelta(d);
        };
        try {
            return OpenAiClient.chat(primary, messages, wrapped, cancel);
        } catch (OpenAiClient.ChatException e) {
            if (fallback == null || got[0] || cancel.cancelled) throw e;
            failedOver = true;
            if (note != null) {
                String name = primary.name == null || primary.name.isEmpty() ? "online" : primary.name;
                note.onNote("Il cervello \"" + name + "\" non risponde (" + shortReason(e.getMessage())
                        + "). Uso quello dentro il tablet: più lento e più semplice.");
            }
            return OpenAiClient.chat(fallback, messages, sink, cancel);
        }
    }

    private static String shortReason(String m) {
        if (m == null) return "errore";
        int dot = m.indexOf('.');
        String s = dot > 0 ? m.substring(0, dot) : m;
        return s.length() > 90 ? s.substring(0, 90) + "…" : s;
    }
}
