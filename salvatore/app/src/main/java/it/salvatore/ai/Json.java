package it.salvatore.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Piccolo lettore/scrittore JSON, senza dipendenze (così si può provare anche fuori da Android). */
public final class Json {

    private Json() {}

    public static Object parse(String s) {
        P p = new P(s);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i != s.length()) throw new IllegalArgumentException("Dati dopo la fine del JSON");
        return v;
    }

    /** Cammina dentro un JSON: chiavi String per gli oggetti, Integer per le liste. Null se manca qualcosa. */
    public static Object path(Object root, Object... keys) {
        Object cur = root;
        for (Object k : keys) {
            if (cur == null) return null;
            if (k instanceof String && cur instanceof Map) {
                cur = ((Map<?, ?>) cur).get(k);
            } else if (k instanceof Integer && cur instanceof List) {
                int idx = (Integer) k;
                List<?> l = (List<?>) cur;
                if (idx < 0 || idx >= l.size()) return null;
                cur = l.get(idx);
            } else {
                return null;
            }
        }
        return cur;
    }

    public static String str(Object o) {
        return o instanceof String ? (String) o : null;
    }

    public static String quote(String s) {
        StringBuilder b = new StringBuilder(s.length() + 2);
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                case '\b': b.append("\\b"); break;
                case '\f': b.append("\\f"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        b.append('"');
        return b.toString();
    }

    public static String write(Object o) {
        StringBuilder b = new StringBuilder();
        write(b, o);
        return b.toString();
    }

    private static void write(StringBuilder b, Object o) {
        if (o == null) {
            b.append("null");
        } else if (o instanceof String) {
            b.append(quote((String) o));
        } else if (o instanceof Boolean) {
            b.append(o.toString());
        } else if (o instanceof Number) {
            double d = ((Number) o).doubleValue();
            if (d == Math.rint(d) && Math.abs(d) < 1e15) b.append((long) d);
            else b.append(d);
        } else if (o instanceof Map) {
            b.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                if (!first) b.append(',');
                first = false;
                b.append(quote(String.valueOf(e.getKey()))).append(':');
                write(b, e.getValue());
            }
            b.append('}');
        } else if (o instanceof List) {
            b.append('[');
            boolean first = true;
            for (Object x : (List<?>) o) {
                if (!first) b.append(',');
                first = false;
                write(b, x);
            }
            b.append(']');
        } else {
            b.append(quote(o.toString()));
        }
    }

    private static final class P {
        final String s;
        int i = 0;

        P(String s) { this.s = s; }

        void ws() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '﻿') i++;
                else break;
            }
        }

        IllegalArgumentException err(String m) {
            return new IllegalArgumentException(m + " (posizione " + i + ")");
        }

        Object value() {
            if (i >= s.length()) throw err("JSON incompleto");
            char c = s.charAt(i);
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            return number();
        }

        Object object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; // {
            ws();
            if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
            while (true) {
                ws();
                if (i >= s.length() || s.charAt(i) != '"') throw err("Atteso nome del campo");
                String k = string();
                ws();
                if (i >= s.length() || s.charAt(i) != ':') throw err("Atteso :");
                i++;
                ws();
                m.put(k, value());
                ws();
                if (i >= s.length()) throw err("Oggetto non chiuso");
                char c = s.charAt(i++);
                if (c == ',') continue;
                if (c == '}') return m;
                throw err("Atteso , oppure }");
            }
        }

        Object array() {
            List<Object> l = new ArrayList<>();
            i++; // [
            ws();
            if (i < s.length() && s.charAt(i) == ']') { i++; return l; }
            while (true) {
                ws();
                l.add(value());
                ws();
                if (i >= s.length()) throw err("Lista non chiusa");
                char c = s.charAt(i++);
                if (c == ',') continue;
                if (c == ']') return l;
                throw err("Atteso , oppure ]");
            }
        }

        String string() {
            StringBuilder b = new StringBuilder();
            i++; // "
            while (true) {
                if (i >= s.length()) throw err("Testo non chiuso");
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c != '\\') { b.append(c); continue; }
                if (i >= s.length()) throw err("Escape incompleto");
                char e = s.charAt(i++);
                switch (e) {
                    case '"': b.append('"'); break;
                    case '\\': b.append('\\'); break;
                    case '/': b.append('/'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'n': b.append('\n'); break;
                    case 'r': b.append('\r'); break;
                    case 't': b.append('\t'); break;
                    case 'u':
                        if (i + 4 > s.length()) throw err("Escape \\u incompleto");
                        try {
                            b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw err("Escape \\u non valido");
                        }
                        i += 4;
                        break;
                    default: throw err("Escape sconosciuto");
                }
            }
        }

        Object number() {
            int st = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (st == i) throw err("Valore non riconosciuto");
            try {
                return Double.valueOf(s.substring(st, i));
            } catch (NumberFormatException ex) {
                throw err("Numero non valido");
            }
        }
    }
}
