package it.salvatore.ai;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Prove della logica di Salvatore (senza Android). Si lanciano con: java it.salvatore.ai.LogicTests */
public class LogicTests {

    static int ok = 0;
    static int bad = 0;

    static void check(boolean cond, String name) {
        if (cond) {
            ok++;
        } else {
            bad++;
            System.out.println("FALLITO: " + name);
        }
    }

    static void eq(Object a, Object b, String name) {
        check(a == null ? b == null : a.equals(b), name + " (atteso <" + b + ">, trovato <" + a + ">)");
    }

    public static void main(String[] args) throws Exception {
        jsonTests();
        endpointTests();
        verdictTests();
        messagesTests();
        pipelineTests();
        clientTests();
        System.out.println("Prove riuscite: " + ok + ", fallite: " + bad);
        if (bad > 0) System.exit(1);
    }

    // ---------- JSON ----------
    static void jsonTests() {
        Object o = Json.parse("{\"a\":[1,2,{\"b\":\"x\\ny\\u00e8\\\"\"}],\"c\":true,\"d\":null,\"e\":-1.5e2}");
        eq(Json.str(Json.path(o, "a", 2, "b")), "x\nyè\"", "json: stringa con escape");
        eq(Json.path(o, "c"), Boolean.TRUE, "json: true");
        eq(Json.path(o, "d"), null, "json: null");
        eq(Json.path(o, "e"), Double.valueOf(-150.0), "json: numero");
        eq(Json.path(o, "a", 9), null, "json: indice fuori");
        eq(Json.path(o, "zzz", "b"), null, "json: chiave assente");

        String s = "Ciao \"mondo\"\n\\ \t \u0001 àèì 😀";
        eq(Json.str(Json.parse(Json.quote(s))), s, "json: quote/parse giro completo");

        Model.Entry e = new Model.Entry("Etichetta", "user", "testo\ncon \"virgolette\"");
        Model.Entry e2 = Model.Entry.fromMap(Json.parse(Json.write(e.toMap())));
        eq(e2.text, e.text, "entry: giro completo testo");
        eq(e2.label, "Etichetta", "entry: giro completo etichetta");

        boolean thrown = false;
        try {
            Json.parse("{\"a\":");
        } catch (IllegalArgumentException ex) {
            thrown = true;
        }
        check(thrown, "json: errore su JSON incompleto");
        thrown = false;
        try {
            Json.parse("{} x");
        } catch (IllegalArgumentException ex) {
            thrown = true;
        }
        check(thrown, "json: errore su dati in eccesso");
    }

    // ---------- indirizzo ----------
    static void endpointTests() {
        eq(OpenAiClient.endpoint("http://127.0.0.1:8080/v1"), "http://127.0.0.1:8080/v1/chat/completions", "endpoint base");
        eq(OpenAiClient.endpoint("http://x/v1/"), "http://x/v1/chat/completions", "endpoint con slash finale");
        eq(OpenAiClient.endpoint("  http://x/v1/chat/completions  "), "http://x/v1/chat/completions", "endpoint già completo");
    }

    // ---------- verdetto ----------
    static void verdictTests() {
        check(Pipeline.isApproved("Approvato"), "verdetto: Approvato");
        check(Pipeline.isApproved("\n  **Approvato**\nTutto ok"), "verdetto: Approvato con markdown");
        check(Pipeline.isApproved("approvato."), "verdetto: minuscolo");
        check(!Pipeline.isApproved("Rifiutato\n- manca il controllo errori"), "verdetto: Rifiutato");
        check(!Pipeline.isApproved("Non approvato"), "verdetto: Non approvato");
        check(!Pipeline.isApproved("Il codice sembra ok, approvato"), "verdetto: frase che non inizia con Approvato");
        check(!Pipeline.isApproved(""), "verdetto: vuoto");
        check(!Pipeline.isApproved(null), "verdetto: null");
    }

    // ---------- messaggi ----------
    static void messagesTests() {
        List<Model.Entry> chat = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            chat.add(new Model.Entry("", i % 2 == 0 ? "user" : "assistant", "m" + i));
        }
        List<Model.Msg> m = Model.buildMessages("  Sei Salvatore  ", chat, 5);
        eq(m.get(0).role, "system", "messaggi: prima le istruzioni");
        eq(m.get(0).content, "Sei Salvatore", "messaggi: istruzioni ripulite");
        eq(m.get(1).role, "user", "messaggi: dopo le istruzioni parla l'utente");
        eq(m.get(m.size() - 1).content, "m9", "messaggi: ultimo messaggio presente");
        check(m.size() <= 6, "messaggi: limite rispettato");
        eq(Model.buildMessages("", chat, 100).size(), 10, "messaggi: senza istruzioni, tutta la chat");
    }

    // ---------- Ingegnere + Revisore ----------
    static class FakeLlm implements Pipeline.Llm {
        final List<String> replies;
        int n = 0;
        final List<List<Model.Msg>> seen = new ArrayList<>();

        FakeLlm(String... r) {
            replies = java.util.Arrays.asList(r);
        }

        public String chat(List<Model.Msg> messages, OpenAiClient.Sink sink) {
            seen.add(messages);
            String r = replies.get(Math.min(n++, replies.size() - 1));
            sink.onDelta(r);
            return r;
        }
    }

    static class Rec implements Pipeline.Events {
        final List<String> labels = new ArrayList<>();
        final StringBuilder streamed = new StringBuilder();
        boolean cancel = false;

        public void begin(String label) {
            labels.add(label);
        }

        public void delta(String t) {
            streamed.append(t);
        }

        public void end(String full) {
        }

        public boolean cancelled() {
            return cancel;
        }
    }

    static void pipelineTests() throws Exception {
        // Approvato al secondo giro
        FakeLlm llm = new FakeLlm("codice v1", "Rifiutato\nmanca X", "codice v2", "Approvato");
        Rec ev = new Rec();
        Pipeline.Result r = Pipeline.run(llm, "ING", "REV", "scrivi qualcosa", ev);
        check(r.approved, "pipeline: approvato");
        eq(r.rounds, 2, "pipeline: due giri");
        eq(r.code, "codice v2", "pipeline: codice finale");
        eq(ev.labels.toString(), "[Ingegnere · giro 1, Revisore · giro 1, Ingegnere · giro 2, Revisore · giro 2]", "pipeline: etichette");
        // Al secondo giro l'Ingegnere deve ricevere codice precedente e correzioni
        String second = llm.seen.get(2).get(1).content;
        check(second.contains("codice v1") && second.contains("manca X") && second.contains("scrivi qualcosa"),
                "pipeline: giro 2 riceve codice e correzioni");
        eq(llm.seen.get(0).get(0).content, "ING", "pipeline: istruzioni Ingegnere");
        eq(llm.seen.get(1).get(0).content, "REV", "pipeline: istruzioni Revisore");

        // Mai approvato: si ferma a 3 giri
        FakeLlm llm2 = new FakeLlm("c", "Rifiutato\nno");
        Pipeline.Result r2 = Pipeline.run(llm2, "ING", "REV", "t", new Rec());
        check(!r2.approved, "pipeline: non approvato dopo il massimo");
        eq(r2.rounds, 3, "pipeline: massimo 3 giri");
        eq(llm2.n, 6, "pipeline: 6 chiamate in tutto");

        // Fermata dall'utente
        Rec ev3 = new Rec();
        ev3.cancel = true;
        FakeLlm llm3 = new FakeLlm("c", "Approvato");
        Pipeline.Result r3 = Pipeline.run(llm3, "ING", "REV", "t", ev3);
        eq(llm3.n, 1, "pipeline: si ferma subito se annullata");
        check(!r3.approved, "pipeline: annullata non è approvata");
    }

    // ---------- Collegamento a un cervello (finto) ----------
    static String readBody(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[2048];
        int n;
        while ((n = in.read(b)) > 0) bo.write(b, 0, n);
        return new String(bo.toByteArray(), StandardCharsets.UTF_8);
    }

    static void clientTests() throws Exception {
        final String[] lastAuth = {null};
        final String[] lastBody = {null};
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        srv.createContext("/stream/v1/chat/completions", ex -> {
            lastAuth[0] = ex.getRequestHeaders().getFirst("Authorization");
            lastBody[0] = readBody(ex.getRequestBody());
            String sse = ": commento\n\n"
                    + "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}\n\n"
                    + "data: {\"choices\":[{\"delta\":{\"content\":\"Ciao \"}}]}\n\n"
                    + "data: {\"choices\":[{\"delta\":{\"content\":\"perché è così\"}}]}\n\n"
                    + "data: [DONE]\n\n";
            byte[] out = sse.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/event-stream");
            ex.sendResponseHeaders(200, out.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(out);
            }
        });
        srv.createContext("/plain/v1/chat/completions", ex -> {
            readBody(ex.getRequestBody());
            byte[] out = "{\"choices\":[{\"message\":{\"content\":\"tutto insieme\"}}]}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, out.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(out);
            }
        });
        srv.createContext("/auth/v1/chat/completions", ex -> {
            readBody(ex.getRequestBody());
            byte[] out = "{\"error\":{\"message\":\"Incorrect API key\"}}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(401, out.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(out);
            }
        });
        srv.createContext("/slow/v1/chat/completions", ex -> {
            readBody(ex.getRequestBody());
            ex.getResponseHeaders().add("Content-Type", "text/event-stream");
            ex.sendResponseHeaders(200, 0);
            try (OutputStream os = ex.getResponseBody()) {
                os.write("data: {\"choices\":[{\"delta\":{\"content\":\"uno \"}}]}\n\n".getBytes(StandardCharsets.UTF_8));
                os.flush();
                for (int i = 0; i < 100; i++) {
                    Thread.sleep(100);
                    os.write("data: {\"choices\":[{\"delta\":{\"content\":\"x\"}}]}\n\n".getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }
            } catch (Exception ignored) {
            }
        });
        srv.start();
        String base = "http://127.0.0.1:" + srv.getAddress().getPort();

        List<Model.Msg> msgs = new ArrayList<>();
        msgs.add(new Model.Msg("system", "Sei \"Salvatore\""));
        msgs.add(new Model.Msg("user", "Ciao\nmondo"));

        // 1) flusso
        Model.Brain b = new Model.Brain("1", "t", base + "/stream/v1", "SEGRETO", "mio-modello");
        StringBuilder got = new StringBuilder();
        String full = OpenAiClient.chat(b, msgs, got::append, new OpenAiClient.Cancel());
        eq(full, "Ciao perché è così", "client: testo completo dal flusso");
        eq(got.toString(), "Ciao perché è così", "client: pezzi ricevuti");
        eq(lastAuth[0], "Bearer SEGRETO", "client: chiave inviata");
        Object body = Json.parse(lastBody[0]);
        eq(Json.str(Json.path(body, "model")), "mio-modello", "client: modello nella richiesta");
        eq(Json.path(body, "stream"), Boolean.TRUE, "client: richiesta a flusso");
        eq(Json.str(Json.path(body, "messages", 0, "content")), "Sei \"Salvatore\"", "client: istruzioni nella richiesta");
        eq(Json.str(Json.path(body, "messages", 1, "content")), "Ciao\nmondo", "client: messaggio utente nella richiesta");

        // 2) risposta non a flusso
        Model.Brain p = new Model.Brain("2", "t", base + "/plain/v1", "", "m");
        eq(OpenAiClient.chat(p, msgs, null, null), "tutto insieme", "client: risposta non a flusso");

        // 3) chiave sbagliata
        Model.Brain a = new Model.Brain("3", "t", base + "/auth/v1", "x", "m");
        try {
            OpenAiClient.chat(a, msgs, null, null);
            check(false, "client: doveva dare errore 401");
        } catch (OpenAiClient.ChatException e) {
            check(e.getMessage().contains("chiave") && e.getMessage().contains("Incorrect API key"), "client: messaggio 401 chiaro: " + e.getMessage());
        }

        // 4) indirizzo inesistente (404)
        Model.Brain nf = new Model.Brain("4", "t", base + "/nonesiste/v1", "", "m");
        try {
            OpenAiClient.chat(nf, msgs, null, null);
            check(false, "client: doveva dare errore 404");
        } catch (OpenAiClient.ChatException e) {
            check(e.getMessage().contains("non trovato"), "client: messaggio 404 chiaro: " + e.getMessage());
        }

        // 5) cervello spento
        Model.Brain off = new Model.Brain("5", "t", "http://127.0.0.1:1/v1", "", "m");
        try {
            OpenAiClient.chat(off, msgs, null, null);
            check(false, "client: doveva dare errore di collegamento");
        } catch (OpenAiClient.ChatException e) {
            check(e.getMessage().contains("collegarmi"), "client: messaggio cervello spento: " + e.getMessage());
        }

        // 6) nessun cervello
        try {
            OpenAiClient.chat(new Model.Brain("6", "t", "", "", ""), msgs, null, null);
            check(false, "client: doveva dire che manca il cervello");
        } catch (OpenAiClient.ChatException e) {
            check(e.getMessage().contains("Impostazioni"), "client: messaggio nessun cervello");
        }

        // 7) stop a metà risposta
        Model.Brain slow = new Model.Brain("7", "t", base + "/slow/v1", "", "m");
        final OpenAiClient.Cancel c = new OpenAiClient.Cancel();
        final StringBuilder part = new StringBuilder();
        new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
            }
            c.cancel();
        }).start();
        long t0 = System.currentTimeMillis();
        String partial = OpenAiClient.chat(slow, msgs, part::append, c);
        long dt = System.currentTimeMillis() - t0;
        check(dt < 4000, "client: lo stop interrompe subito (" + dt + " ms)");
        check(partial.startsWith("uno "), "client: testo parziale conservato");

        srv.stop(0);
    }
}
