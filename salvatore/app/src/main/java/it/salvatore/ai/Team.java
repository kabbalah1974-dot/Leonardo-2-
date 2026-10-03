package it.salvatore.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * La squadra: un capo divide il lavoro, i collaboratori lavorano insieme, il capo mette tutto in una risposta.
 */
public final class Team {

    private Team() {}

    public static final class Worker {
        public final String name;
        public final Pipeline.Llm llm;

        public Worker(String name, Pipeline.Llm llm) {
            this.name = name;
            this.llm = llm;
        }
    }

    /** Cosa succede, da mostrare a schermo. key = quale riquadro (plan, w0, w1…, final). */
    public interface Events {
        void begin(String key, String label);

        void delta(String key, String text);

        void end(String key);

        boolean cancelled();
    }

    public static final class Result {
        public final String finalText;
        public final int answered;

        Result(String finalText, int answered) {
            this.finalText = finalText;
            this.answered = answered;
        }
    }

    public static final String PROMPT_PLAN =
            "Sei il capo di una squadra di assistenti AI. Ricevi un compito e devi dividerlo in parti indipendenti, "
            + "una per ogni collaboratore, in modo che lavorino insieme senza ripetersi. "
            + "Rispondi SOLO con una riga per collaboratore, in questo formato esatto:\nNOME: cosa deve fare\n"
            + "Niente altro testo. Le parti devono essere chiare e complete. Se il compito è semplice, "
            + "dai a ognuno un punto di vista diverso (per esempio uno scrive, uno controlla gli errori).";

    public static final String PROMPT_WORK =
            "Sei un collaboratore in una squadra. Fai SOLO la tua parte, in modo completo e preciso, senza premesse. "
            + "Rispondi in italiano con parole semplici.";

    public static final String PROMPT_FINAL =
            "Sei il capo di una squadra. Hai ricevuto i risultati dei collaboratori. Mettili insieme in UNA sola risposta "
            + "chiara e ordinata per l'utente, in italiano con parole semplici. Se due collaboratori si contraddicono, "
            + "scegli la versione più corretta e dillo in una riga. Non nominare i collaboratori e non fare un elenco di chi ha fatto cosa.";

    /** Legge il piano del capo: righe "NOME: compito". Restituisce un compito per collaboratore (null se non assegnato). */
    public static String[] parsePlan(String plan, List<String> names) {
        String[] out = new String[names.size()];
        if (plan == null) return out;
        for (String raw : plan.split("\n")) {
            String line = raw.trim();
            while (line.startsWith("-") || line.startsWith("*") || line.startsWith("•")) line = line.substring(1).trim();
            line = line.replace("**", "");
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String who = line.substring(0, colon).trim().toLowerCase();
            String what = line.substring(colon + 1).trim();
            if (what.isEmpty()) continue;
            for (int i = 0; i < names.size(); i++) {
                if (out[i] == null && who.equals(names.get(i).trim().toLowerCase())) {
                    out[i] = what;
                    break;
                }
            }
        }
        return out;
    }

    public static Result run(Pipeline.Llm leader, final List<Worker> workers, final String task, String context,
                             final Events ev) throws OpenAiClient.ChatException {
        final String ctx = context == null || context.trim().isEmpty() ? ""
                : "\n\nScheda del progetto (condivisa dalla squadra):\n" + context.trim();
        final List<String> names = new ArrayList<>();
        for (Worker w : workers) names.add(w.name);

        // 1. piano
        ev.begin("plan", "⚙ Piano del capo");
        List<Model.Msg> pm = new ArrayList<>();
        pm.add(new Model.Msg("system", PROMPT_PLAN + ctx));
        pm.add(new Model.Msg("user", "Collaboratori: " + String.join(", ", names) + "\n\nCompito:\n" + task));
        String plan;
        try {
            plan = leader.chat(pm, t -> ev.delta("plan", t));
        } finally {
            ev.end("plan");
        }
        if (ev.cancelled()) return new Result("", 0);
        final String[] parts = parsePlan(plan, names);
        boolean any = false;
        for (String p : parts) if (p != null) any = true;
        if (!any) for (int i = 0; i < parts.length; i++) parts[i] = task; // piano illeggibile: tutti sul compito intero

        // 2. lavoro in parallelo
        final String[] results = new String[workers.size()];
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < workers.size(); i++) {
            if (parts[i] == null) continue;
            final int idx = i;
            final String key = "w" + i;
            Thread th = new Thread(() -> {
                ev.begin(key, "⚙ " + workers.get(idx).name);
                List<Model.Msg> wm = new ArrayList<>();
                wm.add(new Model.Msg("system", PROMPT_WORK + ctx));
                wm.add(new Model.Msg("user", "Compito generale:\n" + task + "\n\nLa tua parte:\n" + parts[idx]));
                try {
                    results[idx] = workers.get(idx).llm.chat(wm, t -> ev.delta(key, t));
                } catch (OpenAiClient.ChatException e) {
                    results[idx] = null;
                    ev.delta(key, "(non ha risposto: " + e.getMessage() + ")");
                } catch (RuntimeException e) {
                    results[idx] = null;
                    ev.delta(key, "(errore inatteso)");
                } finally {
                    ev.end(key);
                }
            });
            threads.add(th);
            th.start();
        }
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (ev.cancelled()) return new Result("", 0);

        // 3. sintesi
        StringBuilder sb = new StringBuilder();
        int answered = 0;
        for (int i = 0; i < workers.size(); i++) {
            if (parts[i] == null) continue;
            if (results[i] == null || results[i].trim().isEmpty()) {
                sb.append("--- ").append(workers.get(i).name).append(": nessuna risposta ---\n\n");
            } else {
                answered++;
                sb.append("--- Parte di ").append(workers.get(i).name).append(" (").append(parts[i]).append(") ---\n")
                        .append(results[i].trim()).append("\n\n");
            }
        }
        if (answered == 0) {
            throw new OpenAiClient.ChatException("Nessun collaboratore ha risposto. Controlla i cervelli e i limiti nelle Impostazioni.");
        }
        ev.begin("final", "Squadra · risposta");
        List<Model.Msg> fm = new ArrayList<>();
        fm.add(new Model.Msg("system", PROMPT_FINAL + ctx));
        fm.add(new Model.Msg("user", "Compito dell'utente:\n" + task + "\n\nRisultati:\n" + sb));
        String fin;
        try {
            fin = leader.chat(fm, t -> ev.delta("final", t));
        } finally {
            ev.end("final");
        }
        return new Result(fin, answered);
    }
}
