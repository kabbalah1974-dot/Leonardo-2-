package it.salvatore.ai;

import java.util.ArrayList;
import java.util.List;

/** Il metodo "Ingegnere scrive → Revisore giudica → correzione", al massimo 3 giri. */
public final class Pipeline {

    private Pipeline() {}

    public static final int MAX_ROUNDS = 3;

    /** Qualunque cosa sappia rispondere a una conversazione. */
    public interface Llm {
        String chat(List<Model.Msg> messages, OpenAiClient.Sink sink) throws OpenAiClient.ChatException;
    }

    /** Cosa succede, passo passo, da mostrare a schermo. */
    public interface Events {
        /** Inizia un nuovo blocco di testo (es. "Ingegnere · giro 1"). */
        void begin(String label);

        void delta(String text);

        /** Il blocco è finito, con il testo completo. */
        void end(String fullText);

        boolean cancelled();
    }

    public static final class Result {
        public final String code;
        public final boolean approved;
        public final int rounds;

        Result(String code, boolean approved, int rounds) {
            this.code = code;
            this.approved = approved;
            this.rounds = rounds;
        }
    }

    /** "Approvato" in prima riga = ok. Tutto il resto (anche "Non approvato") = da correggere. */
    public static boolean isApproved(String review) {
        if (review == null) return false;
        for (String line : review.split("\n")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            int i = 0;
            while (i < t.length() && !Character.isLetter(t.charAt(i))) i++; // salta **, #, ecc.
            return t.substring(i).toLowerCase(java.util.Locale.ROOT).startsWith("approvato");
        }
        return false;
    }

    public static Result run(Llm llm, String engineerPrompt, String reviewerPrompt, String task, Events ev)
            throws OpenAiClient.ChatException {
        String code = "";
        String feedback = "";
        for (int round = 1; round <= MAX_ROUNDS; round++) {
            List<Model.Msg> em = new ArrayList<>();
            em.add(new Model.Msg("system", engineerPrompt));
            if (round == 1) {
                em.add(new Model.Msg("user", task));
            } else {
                em.add(new Model.Msg("user", "Richiesta originale:\n" + task
                        + "\n\nCodice precedente:\n" + code
                        + "\n\nCorrezioni chieste dal Revisore:\n" + feedback
                        + "\n\nApplica tutte le correzioni e restituisci il codice completo."));
            }
            ev.begin("Ingegnere · giro " + round);
            code = llm.chat(em, ev::delta);
            ev.end(code);
            if (ev.cancelled()) return new Result(code, false, round);

            List<Model.Msg> rm = new ArrayList<>();
            rm.add(new Model.Msg("system", reviewerPrompt));
            rm.add(new Model.Msg("user", "Richiesta:\n" + task + "\n\nCodice da controllare:\n" + code));
            ev.begin("Revisore · giro " + round);
            String review = llm.chat(rm, ev::delta);
            ev.end(review);
            if (ev.cancelled()) return new Result(code, false, round);

            if (isApproved(review)) return new Result(code, true, round);
            feedback = review;
        }
        return new Result(code, false, MAX_ROUNDS);
    }
}
