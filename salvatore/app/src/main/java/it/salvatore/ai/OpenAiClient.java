package it.salvatore.ai;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parla con qualunque cervello compatibile OpenAI (POST .../chat/completions, risposta a flusso).
 * Per cambiare cervello basta cambiare indirizzo, chiave e nome del modello.
 */
public final class OpenAiClient implements Pipeline.Llm {

    public interface Sink {
        void onDelta(String text);
    }

    /** Chi sa rispondere "dentro il tablet" (senza internet). Lo imposta l'app all'avvio. */
    public interface LocalEngine {
        String chat(Model.Brain brain, List<Model.Msg> messages, Sink sink, Cancel cancel) throws ChatException;
    }

    public static volatile LocalEngine local;

    /** Permette di fermare una risposta in corso. */
    public static final class Cancel {
        public volatile boolean cancelled = false;
        volatile HttpURLConnection conn;
        private volatile Runnable onCancel;

        /** Cosa fare quando si preme Ferma (se già premuto, lo fa subito). */
        public void setOnCancel(Runnable r) {
            onCancel = r;
            if (r != null && cancelled) r.run();
        }

        public void cancel() {
            cancelled = true;
            HttpURLConnection c = conn;
            if (c != null) {
                try {
                    c.disconnect();
                } catch (RuntimeException ignored) {
                }
            }
            Runnable r = onCancel;
            if (r != null) {
                try {
                    r.run();
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    /** Errore già scritto in italiano, pronto da mostrare. */
    public static final class ChatException extends Exception {
        public ChatException(String message) {
            super(message);
        }
    }

    private final Model.Brain brain;
    private final Cancel cancel;

    public OpenAiClient(Model.Brain brain, Cancel cancel) {
        this.brain = brain;
        this.cancel = cancel;
    }

    @Override
    public String chat(List<Model.Msg> messages, Sink sink) throws ChatException {
        return chat(brain, messages, sink, cancel);
    }

    public static String endpoint(String base) {
        String b = base == null ? "" : base.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        if (b.endsWith("/chat/completions")) return b;
        return b + "/chat/completions";
    }

    static String requestBody(Model.Brain brain, List<Model.Msg> messages) {
        List<Object> ms = new ArrayList<>();
        for (Model.Msg m : messages) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("role", m.role);
            o.put("content", m.content);
            ms.add(o);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", brain.model);
        body.put("messages", ms);
        body.put("stream", Boolean.TRUE);
        return Json.write(body);
    }

    public static String chat(Model.Brain brain, List<Model.Msg> messages, Sink sink, Cancel cancel)
            throws ChatException {
        if (brain != null && brain.isLocal()) {
            LocalEngine le = local;
            if (le == null) throw new ChatException("Il cervello dentro il tablet non è pronto. Riapri Salvatore.");
            return le.chat(brain, messages, sink, cancel == null ? new Cancel() : cancel);
        }
        if (brain == null || brain.url == null || brain.url.trim().isEmpty()) {
            throw new ChatException("Nessun cervello collegato. Apri Impostazioni e scegli un cervello.");
        }
        String address = endpoint(brain.url);
        StringBuilder full = new StringBuilder();
        HttpURLConnection conn = null;
        try {
            URL url = new URL(address);
            conn = (HttpURLConnection) url.openConnection();
            if (cancel != null) cancel.conn = conn;
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(300000); // un tablet lento può metterci parecchio a iniziare
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "text/event-stream, application/json");
            String key = brain.key == null ? "" : brain.key.trim();
            if (!key.isEmpty()) conn.setRequestProperty("Authorization", "Bearer " + key);

            byte[] data = requestBody(brain, messages).getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(data);
            }

            int code = conn.getResponseCode();
            if (code >= 400) {
                throw new ChatException(httpError(code, readAll(conn.getErrorStream())));
            }

            StringBuilder raw = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (cancel != null && cancel.cancelled) break;
                    String t = line.trim();
                    if (t.isEmpty() || t.startsWith(":")) continue;
                    if (t.startsWith("data:")) {
                        String payload = t.substring(5).trim();
                        if (payload.equals("[DONE]")) break;
                        Object o;
                        try {
                            o = Json.parse(payload);
                        } catch (IllegalArgumentException ex) {
                            continue; // riga strana: si ignora
                        }
                        String err = errorText(o);
                        if (err != null) throw new ChatException("Il cervello ha risposto con un errore: " + err);
                        String piece = Json.str(Json.path(o, "choices", 0, "delta", "content"));
                        if (piece != null && !piece.isEmpty()) {
                            full.append(piece);
                            if (sink != null) sink.onDelta(piece);
                        }
                    } else if (raw.length() < 2_000_000) {
                        raw.append(line).append('\n');
                    }
                }
            }

            if (full.length() == 0 && raw.length() > 0 && !(cancel != null && cancel.cancelled)) {
                // Alcuni cervelli rispondono tutto insieme invece che a flusso.
                try {
                    Object o = Json.parse(raw.toString());
                    String err = errorText(o);
                    if (err != null) throw new ChatException("Il cervello ha risposto con un errore: " + err);
                    String msg = Json.str(Json.path(o, "choices", 0, "message", "content"));
                    if (msg != null) {
                        full.append(msg);
                        if (sink != null) sink.onDelta(msg);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (full.length() == 0 && !(cancel != null && cancel.cancelled)) {
                throw new ChatException("Il cervello non ha dato nessuna risposta. Controlla il nome del modello.");
            }
            return full.toString();
        } catch (ChatException e) {
            throw e;
        } catch (UnknownHostException e) {
            throw new ChatException("Non trovo l'indirizzo del cervello (" + address + "). Controlla di essere online e che l'indirizzo sia giusto.");
        } catch (SocketTimeoutException e) {
            throw new ChatException("Il cervello ci mette troppo a rispondere. Riprova, o scegli un modello più piccolo.");
        } catch (IOException e) {
            if (cancel != null && cancel.cancelled) return full.toString();
            throw new ChatException("Non riesco a collegarmi al cervello (" + address + "). Controlla che sia acceso e raggiungibile. Dettaglio: " + e.getMessage());
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (RuntimeException ignored) {
                }
            }
            if (cancel != null) cancel.conn = null;
        }
    }

    static String errorText(Object o) {
        Object e = Json.path(o, "error");
        if (e == null) return null;
        String m = Json.str(Json.path(e, "message"));
        if (m != null) return m;
        if (e instanceof String) return (String) e;
        return "errore sconosciuto";
    }

    static String httpError(int code, String body) {
        String detail = "";
        try {
            String m = errorText(Json.parse(body));
            if (m != null) detail = m;
        } catch (RuntimeException ignored) {
        }
        if (detail.isEmpty() && body != null) {
            String b = body.trim();
            detail = b.length() > 300 ? b.substring(0, 300) : b;
        }
        String base;
        if (code == 401 || code == 403) base = "Il cervello ha rifiutato l'accesso: la chiave manca o non è valida.";
        else if (code == 404) base = "Indirizzo o nome del modello non trovato. Controlla l'indirizzo e il modello nelle Impostazioni.";
        else if (code == 429) base = "Troppe richieste o limite raggiunto. Aspetta un poco e riprova.";
        else if (code >= 500) base = "Il cervello ha un problema (errore " + code + "). Riprova tra poco.";
        else base = "Il cervello ha risposto con l'errore " + code + ".";
        return detail.isEmpty() ? base : base + " Dettaglio: " + detail;
    }

    private static String readAll(InputStream in) {
        if (in == null) return "";
        try (InputStream is = in) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0 && bo.size() < 100_000) bo.write(buf, 0, n);
            return new String(bo.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
