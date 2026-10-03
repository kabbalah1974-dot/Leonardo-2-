package it.salvatore.ai;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Salva e legge la scheda del progetto in un file di un deposito GitHub (API "contents"). */
public final class GitHubSync {

    public static final class Remote {
        public final String text;
        public final String sha;

        Remote(String text, String sha) {
            this.text = text;
            this.sha = sha;
        }
    }

    private final String api;

    public GitHubSync() {
        this("https://api.github.com");
    }

    public GitHubSync(String api) {
        this.api = api;
    }

    private String url(String repo, String path) throws OpenAiClient.ChatException {
        String r = repo == null ? "" : repo.trim();
        String p = path == null ? "" : path.trim();
        while (p.startsWith("/")) p = p.substring(1);
        if (!r.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            throw new OpenAiClient.ChatException("Il deposito va scritto così: nome-utente/nome-deposito (per esempio kabbalah1974-dot/Leonardo-2-).");
        }
        if (p.isEmpty() || p.contains(" ") || p.contains("..")) {
            throw new OpenAiClient.ChatException("Il nome del file non è valido. Esempio: progetto/SALVATORE.md");
        }
        return api + "/repos/" + r + "/contents/" + p;
    }

    private static String needToken(String token) throws OpenAiClient.ChatException {
        String t = token == null ? "" : token.trim();
        if (t.isEmpty()) throw new OpenAiClient.ChatException("Manca la chiave GitHub. Inseriscila nelle Impostazioni, nella sezione Progetto.");
        return t;
    }

    private HttpURLConnection open(String address, String method, String token) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestMethod(method);
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "Salvatore");
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        return c;
    }

    /** Legge il file. Restituisce null se non esiste ancora. */
    public Remote get(String repo, String path, String token) throws OpenAiClient.ChatException {
        String address = url(repo, path);
        String t = needToken(token);
        HttpURLConnection c = null;
        try {
            c = open(address, "GET", t);
            int code = c.getResponseCode();
            if (code == 404) return null;
            if (code >= 400) throw new OpenAiClient.ChatException(error(code, read(c.getErrorStream())));
            Object o = Json.parse(read(c.getInputStream()));
            String content = Json.str(Json.path(o, "content"));
            String sha = Json.str(Json.path(o, "sha"));
            if (content == null || sha == null) {
                throw new OpenAiClient.ChatException("GitHub ha risposto in un modo che non capisco. Controlla che il nome sia un file e non una cartella.");
            }
            String text = new String(Base64.getMimeDecoder().decode(content), StandardCharsets.UTF_8);
            return new Remote(text, sha);
        } catch (OpenAiClient.ChatException e) {
            throw e;
        } catch (UnknownHostException e) {
            throw new OpenAiClient.ChatException("Non riesco a raggiungere GitHub. Controlla internet.");
        } catch (IOException | RuntimeException e) {
            throw new OpenAiClient.ChatException("Errore parlando con GitHub: " + e.getMessage());
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** Scrive il file (lo crea se manca). */
    public void put(String repo, String path, String token, String text, String sha, String message)
            throws OpenAiClient.ChatException {
        String address = url(repo, path);
        String t = needToken(token);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("content", Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)));
        if (sha != null) body.put("sha", sha);
        HttpURLConnection c = null;
        try {
            c = open(address, "PUT", t);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream os = c.getOutputStream()) {
                os.write(Json.write(body).getBytes(StandardCharsets.UTF_8));
            }
            int code = c.getResponseCode();
            if (code >= 400) throw new OpenAiClient.ChatException(error(code, read(c.getErrorStream())));
        } catch (OpenAiClient.ChatException e) {
            throw e;
        } catch (UnknownHostException e) {
            throw new OpenAiClient.ChatException("Non riesco a raggiungere GitHub. Controlla internet.");
        } catch (IOException | RuntimeException e) {
            throw new OpenAiClient.ChatException("Errore parlando con GitHub: " + e.getMessage());
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /**
     * Allinea tablet e GitHub: legge GitHub, unisce con la scheda del tablet, scrive se serve.
     * Restituisce il testo finale (da tenere sia sul tablet sia come "ultima versione sincronizzata").
     */
    public String sync(String repo, String path, String token, String base, String local, boolean write)
            throws OpenAiClient.ChatException {
        Remote r = get(repo, path, token);
        if (r == null) {
            if (write) put(repo, path, token, local, null, "Salvatore: crea la scheda del progetto");
            return local;
        }
        String merged = Project.merge(base, local, r.text);
        if (write && !merged.equals(r.text)) {
            put(repo, path, token, merged, r.sha, "Salvatore: aggiorna la scheda del progetto");
        }
        return merged;
    }

    static String error(int code, String body) {
        String detail = "";
        try {
            String m = Json.str(Json.path(Json.parse(body), "message"));
            if (m != null) detail = m;
        } catch (RuntimeException ignored) {
        }
        String base;
        if (code == 401) base = "GitHub non accetta la chiave: è sbagliata o scaduta.";
        else if (code == 403) base = "GitHub ha rifiutato: la chiave non ha il permesso di scrivere in questo deposito (serve \"Contents: Read and write\"), oppure troppe richieste.";
        else if (code == 404) base = "Deposito non trovato, oppure la chiave non lo può vedere. Controlla il nome e i permessi della chiave.";
        else if (code == 409 || code == 422) base = "Il file è cambiato nel frattempo. Riprova.";
        else base = "GitHub ha risposto con l'errore " + code + ".";
        return detail.isEmpty() ? base : base + " Dettaglio: " + detail;
    }

    private static String read(InputStream in) {
        if (in == null) return "";
        try (InputStream is = in) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0 && bo.size() < 2_000_000) bo.write(buf, 0, n);
            return new String(bo.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
