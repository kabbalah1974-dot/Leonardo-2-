package it.salvatore.ai;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

/** Memoria di Salvatore sul tablet: cervelli, agenti e chat. Resta tutto sul dispositivo. */
public final class Store {

    private static final int MAX_ENTRIES = 300;

    private final SharedPreferences sp;

    public Store(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("salvatore", Context.MODE_PRIVATE);
    }

    // ---- cervelli ----
    /** Quello che c'è davvero salvato (senza il cervello di partenza aggiunto quando la lista è vuota). */
    private List<Model.Brain> brainsStored() {
        List<Model.Brain> out = new ArrayList<>();
        Object o = read("brains");
        if (o instanceof List) {
            for (Object x : (List<?>) o) {
                Model.Brain b = Model.Brain.fromMap(x);
                if (b != null) out.add(b);
            }
        }
        return out;
    }

    public String keyLog() {
        return sp.getString("keylog", "");
    }

    public List<Model.Brain> brains() {
        List<Model.Brain> out = new ArrayList<>();
        Object o = read("brains");
        if (o instanceof List) {
            for (Object x : (List<?>) o) {
                Model.Brain b = Model.Brain.fromMap(x);
                if (b != null) out.add(b);
            }
        }
        if (out.isEmpty()) {
            String[] p = Presets.BRAINS[0];
            out.add(new Model.Brain("locale", p[0], p[1], "", p[2]));
        }
        return out;
    }

    public void saveBrains(List<Model.Brain> brains) {
        saveBrains(brains, null);
    }

    /** Salva i cervelli. Una chiave già presente non si cancella, a meno che l'utente l'abbia tolta apposta (cleared). */
    public void saveBrains(List<Model.Brain> brains, java.util.Set<String> cleared) {
        List<String> saved = KeyGuard.protect(brains, brainsStored(), cleared);
        if (!saved.isEmpty()) {
            StringBuilder where = new StringBuilder();
            StackTraceElement[] st = new Throwable().getStackTrace();
            for (int i = 1; i < Math.min(st.length, 7); i++) {
                where.append(st[i].getClassName().replace("it.salvatore.ai.", "")).append('.')
                        .append(st[i].getMethodName()).append(':').append(st[i].getLineNumber()).append(' ');
            }
            String line = java.text.DateFormat.getDateTimeInstance().format(new java.util.Date()) + " · salvata la chiave di "
                    + String.join(", ", saved) + " · " + where + "\n";
            String log = sp.getString("keylog", "") + line;
            if (log.length() > 3000) log = log.substring(log.length() - 3000);
            sp.edit().putString("keylog", log).apply();
        }
        List<Object> l = new ArrayList<>();
        for (Model.Brain b : brains) l.add(b.toMap());
        sp.edit().putString("brains", Json.write(l)).apply();
    }

    public Model.Brain activeBrain() {
        List<Model.Brain> l = brains();
        String id = sp.getString("brain", "");
        for (Model.Brain b : l) if (b.id.equals(id)) return b;
        return l.get(0);
    }

    public void setActiveBrainId(String id) {
        sp.edit().putString("brain", id).apply();
    }

    /** Passare da solo dal cervello online a quello dentro il tablet (e viceversa). Acceso di partenza. */
    public boolean autoSwitch() {
        return sp.getBoolean("auto", true);
    }

    public void setAutoSwitch(boolean on) {
        sp.edit().putBoolean("auto", on).apply();
    }

    // ---- progetto condiviso e GitHub ----
    public String project() {
        return sp.getString("proj", "");
    }

    public void setProject(String t) {
        sp.edit().putString("proj", t == null ? "" : t).apply();
    }

    /** Ultima versione della scheda già allineata con GitHub. */
    public String projectBase() {
        return sp.getString("proj_base", "");
    }

    public void setProjectBase(String t) {
        sp.edit().putString("proj_base", t == null ? "" : t).apply();
    }

    public String ghToken() {
        return sp.getString("gh_token", "");
    }

    public String ghRepo() {
        return sp.getString("gh_repo", "kabbalah1974-dot/Leonardo-2-");
    }

    public String ghPath() {
        return sp.getString("gh_path", "progetto/SALVATORE.md");
    }

    public void setGh(String token, String repo, String path) {
        sp.edit().putString("gh_token", token).putString("gh_repo", repo).putString("gh_path", path).apply();
    }

    /** Salvare da solo su GitHub dopo ogni risposta. */
    public boolean ghAuto() {
        return sp.getBoolean("gh_auto", true);
    }

    public void setGhAuto(boolean on) {
        sp.edit().putBoolean("gh_auto", on).apply();
    }

    /** Far rispondere insieme tutti i cervelli online che hanno una chiave. */
    public boolean parallel() {
        return sp.getBoolean("parallel", false);
    }

    public void setParallel(boolean on) {
        sp.edit().putBoolean("parallel", on).apply();
    }

    /** Il cervello che fa da capo squadra (vuoto = il cervello in uso). */
    public String leaderId() {
        return sp.getString("leader", "");
    }

    public void setLeaderId(String id) {
        sp.edit().putString("leader", id == null ? "" : id).apply();
    }

    // ---- agenti ----
    public List<Model.Agent> agents() {
        List<Model.Agent> out = new ArrayList<>();
        Object o = read("agents");
        if (o instanceof List) {
            for (Object x : (List<?>) o) {
                Model.Agent a = Model.Agent.fromMap(x);
                if (a != null) out.add(a);
            }
        }
        if (out.isEmpty()) out = Presets.defaultAgents();
        return out;
    }

    public void saveAgents(List<Model.Agent> agents) {
        List<Object> l = new ArrayList<>();
        for (Model.Agent a : agents) l.add(a.toMap());
        sp.edit().putString("agents", Json.write(l)).apply();
    }

    /** Istruzioni di un agente; se è stato tolto, si usano quelle di base. */
    public String promptOf(String id) {
        for (Model.Agent a : agents()) if (a.id.equals(id)) return a.prompt;
        if (Presets.ID_INGEGNERE.equals(id)) return Presets.PROMPT_INGEGNERE;
        if (Presets.ID_REVISORE.equals(id)) return Presets.PROMPT_REVISORE;
        return Presets.PROMPT_SALVATORE;
    }

    // ---- modalità scelta ----
    public String mode() {
        return sp.getString("mode", Presets.ID_SALVATORE);
    }

    public void setMode(String id) {
        sp.edit().putString("mode", id).apply();
    }

    // ---- chat ----
    public List<Model.Entry> chat(String key) {
        List<Model.Entry> out = new ArrayList<>();
        Object o = read("chat_" + key);
        if (o instanceof List) {
            for (Object x : (List<?>) o) {
                Model.Entry e = Model.Entry.fromMap(x);
                if (e != null) out.add(e);
            }
        }
        return out;
    }

    public void saveChat(String key, List<Model.Entry> chat) {
        int from = Math.max(0, chat.size() - MAX_ENTRIES);
        List<Object> l = new ArrayList<>();
        for (int i = from; i < chat.size(); i++) l.add(chat.get(i).toMap());
        sp.edit().putString("chat_" + key, Json.write(l)).apply();
    }

    private Object read(String key) {
        String raw = sp.getString(key, null);
        if (raw == null) return null;
        try {
            return Json.parse(raw);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
