package it.leonardo.antivirus;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Chiede a VirusTotal cosa sa di un file, usando solo la sua impronta (SHA-256).
 * Il file non viene mai inviato. Chiamare da un thread in background.
 */
public final class VirusTotalClient {

    private static final String BASE = "https://www.virustotal.com/api/v3/files/";
    private static final int MAX_BODY_CHARS = 8_000_000;

    private volatile HttpURLConnection current;

    /** Interrompe subito la richiesta in corso (chiamabile da qualunque thread). */
    public void abort() {
        HttpURLConnection c = current;
        if (c != null) c.disconnect();
    }

    public VtResult lookup(String sha256, String apiKey) {
        if (!Hashing.isSha256Hex(sha256)) return VtResult.failed("impronta non valida");
        if (apiKey == null || apiKey.trim().isEmpty()) return VtResult.of(VtResult.Kind.BAD_KEY);

        HttpURLConnection connection = null;
        try {
            URL url = new URL(BASE + sha256.toLowerCase(java.util.Locale.ROOT));
            connection = (HttpURLConnection) url.openConnection();
            current = connection;
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(20_000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("x-apikey", apiKey.trim());
            connection.setRequestProperty("accept", "application/json");
            int code = connection.getResponseCode();
            switch (code) {
                case 200:
                    return parse(readLimited(connection.getInputStream()));
                case 404:
                    return VtResult.of(VtResult.Kind.NOT_FOUND);
                case 401:
                case 403:
                    return VtResult.of(VtResult.Kind.BAD_KEY);
                case 429:
                    return VtResult.forTooManyRequests(readErrorBody(connection));
                default:
                    return VtResult.failed("errore " + code);
            }
        } catch (TooBigException e) {
            return VtResult.failed("risposta troppo grande");
        } catch (IOException e) {
            return VtResult.failed("connessione assente, troppo lenta o interrotta");
        } catch (RuntimeException e) {
            return VtResult.failed("richiesta non valida");
        } finally {
            if (current == connection) current = null; // non cancellare la connessione di un controllo più nuovo
            if (connection != null) connection.disconnect();
        }
    }

    private static VtResult parse(String body) {
        try {
            JSONObject attributes = new JSONObject(body).getJSONObject("data").getJSONObject("attributes");
            JSONObject stats = attributes.optJSONObject("last_analysis_stats");
            if (stats == null) return VtResult.failed("file noto ma non ancora analizzato");
            return VtResult.fromStats(stats.optInt("malicious"), stats.optInt("suspicious"),
                    stats.optInt("harmless"), stats.optInt("undetected"));
        } catch (Exception e) {
            return VtResult.failed("risposta non leggibile");
        }
    }

    private static final class TooBigException extends IOException {
        private static final long serialVersionUID = 1L;

        TooBigException() {
            super("risposta troppo grande");
        }
    }

    /** Corpo dell'errore, letto con prudenza: se non si riesce, stringa vuota. */
    private static String readErrorBody(HttpURLConnection connection) {
        try {
            InputStream err = connection.getErrorStream();
            return err == null ? "" : readLimited(err);
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    /** Legge al massimo MAX_BODY_CHARS caratteri: una risposta gigante non deve riempire la memoria. */
    private static String readLimited(InputStream in) throws IOException {
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = reader.read(buf)) != -1) {
                if (sb.length() + n > MAX_BODY_CHARS) throw new TooBigException();
                sb.append(buf, 0, n);
            }
            return sb.toString();
        }
    }
}
