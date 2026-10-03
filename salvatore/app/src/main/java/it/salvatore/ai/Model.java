package it.salvatore.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** I "mattoncini" dati di Salvatore: messaggi, cervelli e agenti. */
public final class Model {

    private Model() {}

    /** Un messaggio mandato al cervello. role = system | user | assistant. */
    public static final class Msg {
        public final String role;
        public final String content;

        public Msg(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    /** Un "cervello": un indirizzo che parla il linguaggio standard (compatibile OpenAI). */
    public static final class Brain {
        public String id;
        public String name;
        public String url;
        public String key;
        public String model;

        public Brain(String id, String name, String url, String key, String model) {
            this.id = id;
            this.name = name;
            this.url = url;
            this.key = key;
            this.model = model;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("name", name);
            m.put("url", url);
            m.put("key", key);
            m.put("model", model);
            return m;
        }

        public static Brain fromMap(Object o) {
            String id = Json.str(Json.path(o, "id"));
            if (id == null) return null;
            return new Brain(id, nz(Json.str(Json.path(o, "name"))), nz(Json.str(Json.path(o, "url"))),
                    nz(Json.str(Json.path(o, "key"))), nz(Json.str(Json.path(o, "model"))));
        }
    }

    /** Un agente: un nome e le istruzioni che ne definiscono ruolo e modo di rispondere. */
    public static final class Agent {
        public String id;
        public String name;
        public String prompt;

        public Agent(String id, String name, String prompt) {
            this.id = id;
            this.name = name;
            this.prompt = prompt;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("name", name);
            m.put("prompt", prompt);
            return m;
        }

        public static Agent fromMap(Object o) {
            String id = Json.str(Json.path(o, "id"));
            if (id == null) return null;
            return new Agent(id, nz(Json.str(Json.path(o, "name"))), nz(Json.str(Json.path(o, "prompt"))));
        }
    }

    /** Una riga della chat mostrata a schermo. label = titoletto (es. "Ingegnere · giro 1"), può essere vuoto. */
    public static final class Entry {
        public final String label;
        public final String role; // user | assistant
        public String text;

        public Entry(String label, String role, String text) {
            this.label = label;
            this.role = role;
            this.text = text;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", label);
            m.put("role", role);
            m.put("text", text);
            return m;
        }

        public static Entry fromMap(Object o) {
            String role = Json.str(Json.path(o, "role"));
            if (role == null) return null;
            return new Entry(nz(Json.str(Json.path(o, "label"))), role, nz(Json.str(Json.path(o, "text"))));
        }
    }

    public static String nz(String s) {
        return s == null ? "" : s;
    }

    /**
     * Prepara i messaggi da mandare al cervello: istruzioni dell'agente + gli ultimi messaggi della chat.
     * I modelli piccoli hanno poca memoria, quindi si tengono solo gli ultimi "max".
     */
    public static List<Msg> buildMessages(String systemPrompt, List<Entry> chat, int max) {
        List<Msg> out = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) out.add(new Msg("system", systemPrompt.trim()));
        int start = Math.max(0, chat.size() - max);
        // Il primo messaggio dopo le istruzioni dev'essere dell'utente.
        while (start < chat.size() && !"user".equals(chat.get(start).role)) start++;
        for (int i = start; i < chat.size(); i++) {
            Entry e = chat.get(i);
            if (e.text == null || e.text.isEmpty()) continue;
            out.add(new Msg(e.role, e.text));
        }
        return out;
    }
}
