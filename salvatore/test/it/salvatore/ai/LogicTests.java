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
        fmtTests();
        bytesTests();
        modelsTests();
        projectTests();
        thinkTests();
        localTests();
        autoTests();
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
    // ---------- testo pulito ----------
    static void fmtTests() {
        eq(Fmt.plain("Apri **Play Store** e tocca __Installa__."), "Apri Play Store e tocca Installa.", "fmt: grassetto");
        eq(Fmt.plain("* uno\n- due\n  * tre"), "• uno\n• due\n  • tre", "fmt: elenchi");
        eq(Fmt.plain("## Titolo\ntesto"), "Titolo\ntesto", "fmt: titoli");
        eq(Fmt.plain("```java\nint a = 2 * 3;\n```"), "int a = 2 * 3;", "fmt: blocchi di codice (il contenuto resta)");
        eq(Fmt.plain("usa `ls` e *corsivo* qui"), "usa ls e corsivo qui", "fmt: backtick e corsivo");
        eq(Fmt.plain("2 * 3 * 4"), "2 * 3 * 4", "fmt: moltiplicazioni non toccate");
        eq(Fmt.plain(""), "", "fmt: vuoto");
        eq(Fmt.plain("riga1\n\nriga3"), "riga1\n\nriga3", "fmt: righe vuote");
    }

    // ---------- pensieri ----------
    static String feedAll(ThinkFilter f, String... parts) {
        StringBuilder b = new StringBuilder();
        for (String p : parts) b.append(f.feed(p));
        b.append(f.finish());
        return b.toString();
    }

    static void thinkTests() {
        eq(feedAll(new ThinkFilter(), "Ciao mondo"), "Ciao mondo", "think: testo normale");
        eq(feedAll(new ThinkFilter(), "<think>ragiono</think>Risposta"), "Risposta", "think: pensiero tolto");
        eq(feedAll(new ThinkFilter(), "<thi", "nk>ragiono", " ancora</th", "ink>\n\nRis", "posta"), "Risposta", "think: tag spezzati");
        eq(feedAll(new ThinkFilter(), "<think>\n\n</think>\n\nOk"), "Ok", "think: pensiero vuoto");
        eq(feedAll(new ThinkFilter(), "a < b e c <t"), "a < b e c <t", "think: segni simili restano");
        eq(feedAll(new ThinkFilter(), "<think>mai chiuso"), "", "think: pensiero mai chiuso");
        eq(feedAll(new ThinkFilter(), "Prima <think>x</think>dopo"), "Prima dopo", "think: pensiero in mezzo");
    }

    // ---------- cervello dentro il tablet ----------
    static void localTests() throws Exception {
        check(new Model.Brain("1", "t", "local:", "", "m").isLocal(), "locale: riconosciuto");
        check(!new Model.Brain("1", "t", "http://x/v1", "", "m").isLocal(), "locale: online non lo è");
        check(!new Model.Brain("1", "t", "", "", "m").isLocal(), "locale: vuoto non lo è");

        List<Model.Msg> msgs = new ArrayList<>();
        msgs.add(new Model.Msg("user", "ciao"));
        Model.Brain lb = new Model.Brain("1", "t", "local:", "", "f.litertlm");

        OpenAiClient.local = null;
        try {
            OpenAiClient.chat(lb, msgs, null, null);
            check(false, "locale: senza motore doveva dare errore");
        } catch (OpenAiClient.ChatException e) {
            check(e.getMessage().contains("non è pronto"), "locale: messaggio senza motore");
        }

        final String[] seen = {null};
        OpenAiClient.local = (b, m, sink, c) -> {
            seen[0] = b.model + ":" + m.size();
            sink.onDelta("ri");
            sink.onDelta("sposta");
            return "risposta";
        };
        StringBuilder got = new StringBuilder();
        eq(OpenAiClient.chat(lb, msgs, got::append, null), "risposta", "locale: risposta del motore");
        eq(got.toString(), "risposta", "locale: pezzi inoltrati");
        eq(seen[0], "f.litertlm:1", "locale: il motore riceve cervello e messaggi");
        OpenAiClient.local = null;

        // Ferma: l'azione registrata parte, anche se registrata dopo
        final int[] hits = {0};
        OpenAiClient.Cancel c1 = new OpenAiClient.Cancel();
        c1.setOnCancel(() -> hits[0]++);
        c1.cancel();
        eq(hits[0], 1, "ferma: azione eseguita");
        OpenAiClient.Cancel c2 = new OpenAiClient.Cancel();
        c2.cancel();
        c2.setOnCancel(() -> hits[0]++);
        eq(hits[0], 2, "ferma: azione registrata dopo viene eseguita subito");
    }
    // ---------- scelta automatica del cervello ----------
    static void autoTests() throws Exception {
        Model.Brain local = new Model.Brain("L", "Tablet", "local:", "", "f.litertlm");
        Model.Brain gem = new Model.Brain("G", "Gemini", "https://x.example/v1", "chiave", "m");
        Model.Brain lan = new Model.Brain("S", "Server di casa", "http://127.0.0.1:8080/v1", "", "m");
        List<Model.Brain> all = new ArrayList<>();
        all.add(lan);
        all.add(local);
        all.add(gem);
        AutoBrain.Ready yes = b -> true;
        AutoBrain.Ready no = b -> false;

        AutoBrain.Choice c = AutoBrain.choose(all, gem, true, true, yes);
        eq(c.primary.id, "G", "auto: online usa l'online");
        eq(c.fallback == null ? null : c.fallback.id, "L", "auto: online ha il tablet di riserva");

        c = AutoBrain.choose(all, gem, true, false, yes);
        eq(c.primary.id, "L", "auto: offline passa al tablet");
        check(c.fallback == null, "auto: offline senza riserva");

        c = AutoBrain.choose(all, local, true, true, yes);
        eq(c.primary.id, "G", "auto: scelto il tablet ma c'è internet, torna all'online");
        eq(c.fallback.id, "L", "auto: riserva dopo il ritorno online");

        c = AutoBrain.choose(all, gem, true, false, no);
        eq(c.primary.id, "G", "auto: offline ma modello non scaricato, resta com'è");

        c = AutoBrain.choose(all, gem, true, true, no);
        check(c.fallback == null, "auto: modello non scaricato, nessuna riserva");

        c = AutoBrain.choose(all, gem, false, false, yes);
        eq(c.primary.id, "G", "auto spento: non cambia mai");
        check(c.fallback == null, "auto spento: nessuna riserva");

        c = AutoBrain.choose(all, lan, true, true, yes);
        eq(c.primary.id, "G", "auto: il server sul tablet stesso non conta come online");

        // SmartBrain: l'online dà errore 503 -> si passa al tablet
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        srv.createContext("/down/v1/chat/completions", ex -> {
            readBody(ex.getRequestBody());
            byte[] out = "{\"error\":{\"message\":\"high demand\"}}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(503, out.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(out);
            }
        });
        srv.start();
        Model.Brain down = new Model.Brain("D", "Giù", "http://127.0.0.1:" + srv.getAddress().getPort() + "/down/v1", "k", "m");
        List<Model.Msg> msgs = new ArrayList<>();
        msgs.add(new Model.Msg("user", "ciao"));
        final int[] localCalls = {0};
        OpenAiClient.local = (b, m, sink, cc) -> {
            localCalls[0]++;
            if (sink != null) sink.onDelta("dal tablet");
            return "dal tablet";
        };
        final List<String> notes = new ArrayList<>();
        SmartBrain sb = new SmartBrain(down, local, new OpenAiClient.Cancel(), notes::add);
        StringBuilder got = new StringBuilder();
        eq(sb.chat(msgs, got::append), "dal tablet", "smart: passa al tablet se l'online dà errore");
        eq(got.toString(), "dal tablet", "smart: pezzi dal tablet");
        eq(notes.size(), 1, "smart: un avviso");
        check(notes.get(0).contains("Giù") && notes.get(0).contains("503"), "smart: avviso chiaro: " + notes);
        sb.chat(msgs, null);
        eq(notes.size(), 1, "smart: dopo il passaggio non ripete l'avviso");
        eq(localCalls[0], 2, "smart: poi usa direttamente il tablet");

        // senza riserva l'errore resta
        SmartBrain sb2 = new SmartBrain(down, null, new OpenAiClient.Cancel(), notes::add);
        try {
            sb2.chat(msgs, null);
            check(false, "smart: senza riserva doveva dare errore");
        } catch (OpenAiClient.ChatException e) {
            check(e.getMessage().contains("problema"), "smart: errore 503 chiaro");
        }
        OpenAiClient.local = null;
        srv.stop(0);
    }
    static void projectTests() throws Exception {
        eq(Project.merge("A\n", "A\n", "A\nR\n"), "A\nR\n", "progetto: tablet fermo, vale GitHub");
        eq(Project.merge("A\n", "A\nL\n", "A\n"), "A\nL\n", "progetto: GitHub fermo, vale tablet");
        eq(Project.merge("A\n", "A\nL\n", "A\nR\n"), "A\nR\nL\n", "progetto: righe in fondo si sommano");
        check(Project.merge("A\n", "X\n", "A\nR\n").contains("Versione del tablet"), "progetto: conflitto tiene tutte e due");
        String d = Project.appendLog("", "- riga");
        check(d.contains(Project.LOG_HEADER) && d.endsWith("- riga\n"), "progetto: registro creato");
        eq(Project.appendLog(d, "a\nb").endsWith("a b\n"), true, "progetto: riga unica");
        check(Project.forPrompt("x".repeat(9000), 3000).length() < 3100, "progetto: taglio per il cervello");
        check(Project.logLine("2026-10-03 13:00", "Salvatore", "ciao?", "ciao a te").startsWith("- 2026-10-03 13:00 · Salvatore"), "progetto: riga di registro");

        List<Model.Entry> ch = new ArrayList<>();
        ch.add(new Model.Entry("", "user", "domanda"));
        ch.add(new Model.Entry("Salvatore · Gemini", "assistant", "uno"));
        ch.add(new Model.Entry("Salvatore · Groq", "assistant", "due"));
        List<Model.Msg> ms = Model.buildMessages("sys", ch, 20);
        eq(ms.size(), 3, "parallelo: risposte unite in un messaggio");
        check(ms.get(2).content.contains("[Salvatore · Gemini] uno") && ms.get(2).content.contains("[Salvatore · Groq] due"), "parallelo: nomi dei cervelli");

        // GitHub finto
        final String[] stored = {null};
        final String[] shaNow = {null};
        final List<String> seen = new ArrayList<>();
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        srv.createContext("/repos/u/r/contents/p/F.md", ex -> {
            seen.add(ex.getRequestMethod() + " auth=" + ex.getRequestHeaders().getFirst("Authorization"));
            byte[] out;
            int code = 200;
            if (ex.getRequestMethod().equals("GET")) {
                if (stored[0] == null) {
                    code = 404;
                    out = "{\"message\":\"Not Found\"}".getBytes(StandardCharsets.UTF_8);
                } else {
                    String b64 = java.util.Base64.getMimeEncoder().encodeToString(stored[0].getBytes(StandardCharsets.UTF_8));
                    out = ("{\"sha\":\"" + shaNow[0] + "\",\"content\":\"" + b64 + "\"}").getBytes(StandardCharsets.UTF_8);
                }
            } else {
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Object o = Json.parse(body);
                stored[0] = new String(java.util.Base64.getDecoder().decode(Json.str(Json.path(o, "content"))), StandardCharsets.UTF_8);
                shaNow[0] = "sha" + stored[0].length();
                out = "{}".getBytes(StandardCharsets.UTF_8);
            }
            ex.sendResponseHeaders(code, out.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(out);
            }
        });
        srv.start();
        GitHubSync g = new GitHubSync("http://127.0.0.1:" + srv.getAddress().getPort());
        eq(g.sync("u/r", "p/F.md", "tok", "", "ciao\nè qui\n", true), "ciao\nè qui\n", "github: crea il file");
        eq(stored[0], "ciao\nè qui\n", "github: file scritto (accenti ok)");
        stored[0] = "ciao\nè qui\nda GitHub\n";
        shaNow[0] = "shaX";
        String res = g.sync("u/r", "p/F.md", "tok", "ciao\nè qui\n", "ciao\nè qui\nlocale\n", true);
        eq(res, "ciao\nè qui\nda GitHub\nlocale\n", "github: unisce le righe");
        eq(stored[0], res, "github: scrive l'unione");
        check(seen.get(0).equals("GET auth=Bearer tok"), "github: manda la chiave");
        try {
            g.sync("brutto", "p/F.md", "tok", "", "x", true);
            check(false, "github: nome deposito sbagliato");
        } catch (OpenAiClient.ChatException e) {
            check(e.getMessage().contains("nome-utente"), "github: errore deposito");
        }
        try {
            g.sync("u/r", "p/F.md", "", "", "x", true);
            check(false, "github: senza chiave");
        } catch (OpenAiClient.ChatException e) {
            check(e.getMessage().contains("chiave"), "github: errore chiave");
        }
        srv.stop(0);
    }
    static void modelsTests() {
        eq(OpenAiClient.modelsEndpoint("https://api.groq.com/openai/v1/"), "https://api.groq.com/openai/v1/models", "modelli: indirizzo");
        eq(OpenAiClient.modelsEndpoint("https://x.it/v1/chat/completions"), "https://x.it/v1/models", "modelli: indirizzo da chat");
        eq(OpenAiClient.parseModels("{\"data\":[{\"id\":\"b\"},{\"id\":\"a\"},{\"id\":\"a\"}]}").toString(), "[a, b]", "modelli: formato OpenAI");
        eq(OpenAiClient.parseModels("{\"data\":[{\"id\":\"models/gemini-x\"}]}").toString(), "[gemini-x]", "modelli: prefisso Gemini");
        eq(OpenAiClient.parseModels("non json").toString(), "[]", "modelli: risposta strana");
        eq(Presets.keyLink("https://api.mistral.ai/v1"), "https://console.mistral.ai/api-keys", "chiave: link Mistral");
        eq(Presets.keyLink("http://127.0.0.1:8080/v1"), "", "chiave: nessun link");
    }
    static void bytesTests() {
        eq(Fmt.fixBytes("Ciao!\u0120\u01D2\u0141\u012C" + "Se hai"), "Ciao! Se hai", "byte: pezzi di emoji tolti");
        eq(Fmt.fixBytes("a\u0120b\u010Ac"), "a b\nc", "byte: spazio e a capo");
        eq(Fmt.fixBytes("È già così, perché sì: àèìòù"), "È già così, perché sì: àèìòù", "byte: l'italiano non si tocca");
        eq(Fmt.fixBytes(""), "", "byte: vuoto");
    }
}
