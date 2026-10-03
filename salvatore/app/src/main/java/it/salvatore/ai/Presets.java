package it.salvatore.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Cervelli e agenti di partenza. */
public final class Presets {

    private Presets() {}

    /** {nome, indirizzo, modello} */
    public static final String[][] BRAINS = {
        {"Dentro il tablet (senza internet)", "local:", "Qwen3-0.6B.litertlm"},
        {"Sul tablet o in rete locale", "http://127.0.0.1:8080/v1", "local"},
        {"Ollama (tablet o computer di casa)", "http://127.0.0.1:11434/v1", "llama3.2"},
        {"Google Gemini (online)", "https://generativelanguage.googleapis.com/v1beta/openai/", "gemini-3.8-flash"},
        {"Groq (online)", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"},
        {"OpenRouter (online)", "https://openrouter.ai/api/v1", "openrouter/auto"},
        {"Mio server (a pagamento)", "", ""},
    };

    /** Modello consigliato per il cervello dentro il tablet (libero, senza account). */
    public static final String LOCAL_FILE = "Qwen3-0.6B.litertlm";
    public static final String LOCAL_URL =
            "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm";
    public static final int LOCAL_MB = 586;

    public static final String ID_SALVATORE = "salvatore";
    public static final String ID_INGEGNERE = "ingegnere";
    public static final String ID_REVISORE = "revisore";

    public static final String PROMPT_SALVATORE =
            "Sei Salvatore, un assistente personale. Rispondi sempre in italiano, con parole semplici e frasi corte. "
            + "Spiega le cose un passo alla volta, senza gergo tecnico. Se non sai una cosa, dillo con onestà.";

    public static final String PROMPT_INGEGNERE =
            "Sei l'Ingegnere. Scrivi solo il codice richiesto, completo e funzionante, seguito da una brevissima "
            + "spiegazione (al massimo 3 righe). Nessun altro testo. Se ricevi le correzioni del Revisore, "
            + "applicale tutte e restituisci il codice completo corretto.";

    public static final String PROMPT_REVISORE =
            "Sei il Revisore. Controlla il codice ricevuto: errori, parti mancanti, sicurezza, aderenza alla richiesta. "
            + "Rispondi così: la prima riga contiene solo la parola Approvato oppure la parola Rifiutato. "
            + "Se rifiuti, sotto elenca in breve i problemi da correggere. Non scrivere codice.";

    public static Model.Agent agentSalvatore() {
        return new Model.Agent(ID_SALVATORE, "Salvatore", PROMPT_SALVATORE);
    }

    public static Model.Agent agentIngegnere() {
        return new Model.Agent(ID_INGEGNERE, "Ingegnere", PROMPT_INGEGNERE);
    }

    public static Model.Agent agentRevisore() {
        return new Model.Agent(ID_REVISORE, "Revisore", PROMPT_REVISORE);
    }

    public static List<Model.Agent> defaultAgents() {
        List<Model.Agent> l = new ArrayList<>();
        l.add(agentSalvatore());
        l.add(agentIngegnere());
        l.add(agentRevisore());
        return l;
    }

    public static boolean isBaseAgent(String id) {
        return ID_SALVATORE.equals(id) || ID_INGEGNERE.equals(id) || ID_REVISORE.equals(id);
    }

    private static final Random RND = new Random();

    public static String newId() {
        return Long.toString(System.currentTimeMillis(), 36) + Integer.toString(RND.nextInt(36 * 36 * 36), 36);
    }
}
