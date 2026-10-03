package it.salvatore.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;

/** Schermata principale di Salvatore: la chat. */
public class MainActivity extends Activity {

    private static final String PIPELINE = "pipeline";
    private static final String SQUAD = "squadra";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private Store store;
    private Ui th;

    private LinearLayout list;
    private ScrollView scroll;
    private EditText input;
    private Button sendBtn;
    private Spinner modeSpinner;
    private TextView brainInfo;
    private ArrayAdapter<String> modeAdapter;

    private final List<String> modeIds = new ArrayList<>();
    private final List<String> modeNames = new ArrayList<>();
    private List<Model.Agent> agents = new ArrayList<>();
    private String modeId = Presets.ID_SALVATORE;
    private List<Model.Entry> chat = new ArrayList<>();

    private boolean busy = false;
    private OpenAiClient.Cancel cancel;
    private final ExecutorService projectWork = Executors.newSingleThreadExecutor();
    private String pendingQ = "";
    private final List<Model.Entry> pendingReplies = new ArrayList<>();
    private Model.Entry liveEntry;
    private TextView liveView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new Store(this);
        th = new Ui(this);
        LocalBrain.install(this);
        buildUi();
        reloadModes();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!busy) reloadModes();
        updateBrainInfo();
    }

    @Override
    protected void onPause() {
        super.onPause();
        store.saveChat(modeId, chat);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        // Con poca memoria libera il modello dentro il tablet: si ricarica da solo alla prossima risposta.
        if (level >= TRIM_MEMORY_MODERATE && !busy) LocalBrain.release();
    }

    /** Il testo come si vede a schermo: senza asterischi e simboli, tranne il codice dell'Ingegnere. */
    private static String shown(Model.Entry e) {
        return e.label.startsWith("Ingegnere") ? e.text : Fmt.plain(e.text);
    }

    // ------------------------------------------------------------------ schermata

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(th.bg);
        root.setPadding(th.dp(16), th.dp(12), th.dp(16), th.dp(12));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = th.label("Salvatore", 26, th.text, true);
        row1.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row1.addView(th.button("Nuova chat", v -> onNewChat()));
        row1.addView(th.button("Impostazioni", v -> startActivity(new Intent(this, SettingsActivity.class))));
        root.addView(row1);

        brainInfo = th.label("", 13, th.sub, false);
        root.addView(brainInfo);

        modeSpinner = new Spinner(this);
        modeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, modeNames);
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modeSpinner.setAdapter(modeAdapter);
        modeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (pos < 0 || pos >= modeIds.size()) return;
                String newId = modeIds.get(pos);
                if (newId.equals(modeId)) return;
                if (busy) {
                    modeSpinner.setSelection(Math.max(0, modeIds.indexOf(modeId)));
                    Toast.makeText(MainActivity.this, "Aspetta che finisca la risposta, o premi Ferma.", Toast.LENGTH_SHORT).show();
                    return;
                }
                switchMode(newId);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        root.addView(modeSpinner);

        scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, th.dp(8), 0, th.dp(8));
        scroll.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.BOTTOM);
        input = th.field("Scrivi a Salvatore…", true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMaxLines(6);
        bar.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        sendBtn = th.button("Invia", v -> onSend());
        bar.addView(sendBtn);
        root.addView(bar);

        setContentView(root);
    }

    private void reloadModes() {
        agents = store.agents();
        modeIds.clear();
        modeNames.clear();
        for (Model.Agent a : agents) {
            modeIds.add(a.id);
            modeNames.add(a.name.isEmpty() ? "(senza nome)" : a.name);
        }
        modeIds.add(PIPELINE);
        modeNames.add("Ingegnere + Revisore (fino a 3 giri)");
        modeIds.add(SQUAD);
        modeNames.add("Squadra (un capo coordina gli altri)");
        modeAdapter.notifyDataSetChanged();

        String wanted = store.mode();
        if (!modeIds.contains(wanted)) wanted = modeIds.get(0);
        boolean changed = !wanted.equals(modeId) || chat.isEmpty();
        modeId = wanted;
        modeSpinner.setSelection(modeIds.indexOf(modeId));
        if (changed) {
            chat = store.chat(modeId);
            render();
        }
    }

    private void updateBrainInfo() {
        Model.Brain b = store.activeBrain();
        if (b.url.isEmpty()) {
            brainInfo.setText("Cervello: non ancora collegato (apri Impostazioni)");
        } else {
            String m = b.model.isEmpty() ? "" : " · " + b.model;
            String a = store.autoSwitch() ? " · automatico" : "";
            brainInfo.setText("Cervello: " + b.name + m + a);
        }
    }

    private void switchMode(String id) {
        store.saveChat(modeId, chat);
        modeId = id;
        store.setMode(id);
        chat = store.chat(id);
        render();
    }

    // ------------------------------------------------------------------ disegno della chat

    private void render() {
        list.removeAllViews();
        liveEntry = null;
        liveView = null;
        if (chat.isEmpty()) {
            list.addView(hint());
            return;
        }
        for (Model.Entry e : chat) addBubble(e);
        scrollDown();
    }

    private View hint() {
        String s = SQUAD.equals(modeId)
                ? "Qui lavora una squadra.\n\nScrivi un compito: il capo lo divide, gli altri cervelli lavorano insieme sulle loro parti "
                  + "e il capo ti dà una sola risposta. Servono almeno 2 cervelli online con la chiave (Impostazioni)."
                : PIPELINE.equals(modeId)
                ? "Qui lavorano in due.\n\nScrivi cosa vuoi programmare: l'Ingegnere scrive il codice, il Revisore lo controlla "
                  + "e, se serve, lo rimanda indietro per le correzioni (fino a 3 giri)."
                : "Ciao, sono Salvatore.\n\nScrivi qui sotto per parlare con me.\n\nPer rispondere ho bisogno di un cervello collegato: "
                  + "apri Impostazioni per sceglierlo.";
        TextView t = th.label(s, 16, th.sub, false);
        t.setPadding(th.dp(8), th.dp(24), th.dp(8), th.dp(8));
        return t;
    }

    private TextView addBubble(Model.Entry e) {
        boolean user = "user".equals(e.role);

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackground(th.rounded(user ? th.userBubble : th.botBubble, 16));
        wrap.setPadding(th.dp(14), th.dp(10), th.dp(14), th.dp(10));

        int maxW = Math.min(th.dp(760), (int) (getResources().getDisplayMetrics().widthPixels * 0.88f));

        final TextView t = new TextView(this);
        t.setText(shown(e));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, e.label.startsWith("Ingegnere") ? 14 : 16);
        t.setTextColor(user ? th.userText : th.text);
        t.setTextIsSelectable(true);
        t.setMaxWidth(maxW);
        if (e.label.startsWith("Ingegnere")) t.setTypeface(Typeface.MONOSPACE);

        if (!user) {
            LinearLayout head = new LinearLayout(this);
            head.setOrientation(LinearLayout.HORIZONTAL);
            TextView l = th.label(e.label, 13, th.accent, true);
            head.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView copy = th.label("Copia", 13, th.accent, true);
            copy.setPadding(th.dp(12), th.dp(4), 0, th.dp(4));
            copy.setOnClickListener(v -> copyText(t.getText().toString()));
            head.addView(copy);
            wrap.addView(head);
        }
        wrap.addView(t);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = user ? Gravity.END : Gravity.START;
        lp.setMargins(0, th.dp(6), 0, th.dp(6));
        list.addView(wrap, lp);
        return t;
    }

    private void addNotice(String s) {
        TextView t = th.label(s, 14, th.danger, false);
        t.setPadding(th.dp(8), th.dp(6), th.dp(8), th.dp(6));
        list.addView(t);
        scrollDown();
    }

    /** Un avviso neutro (non è un errore), ad esempio quando si passa al cervello dentro il tablet. */
    private void addInfo(String s) {
        TextView t = th.label(s, 14, th.sub, false);
        t.setPadding(th.dp(8), th.dp(6), th.dp(8), th.dp(6));
        list.addView(t);
        scrollDown();
    }

    private void scrollDown() {
        scroll.post(() -> scroll.scrollTo(0, list.getBottom()));
    }

    private void copyText(String s) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("Salvatore", s));
            Toast.makeText(this, "Copiato", Toast.LENGTH_SHORT).show();
        }
    }

    private void setBusy(boolean b) {
        busy = b;
        sendBtn.setText(b ? "Ferma" : "Invia");
        modeSpinner.setEnabled(!b);
    }

    private void onNewChat() {
        if (busy) {
            Toast.makeText(this, "Aspetta che finisca la risposta, o premi Ferma.", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Nuova chat")
                .setMessage("Cancello questa conversazione e ricomincio da capo?")
                .setPositiveButton("Sì, cancella", (d, w) -> {
                    chat.clear();
                    store.saveChat(modeId, chat);
                    render();
                })
                .setNegativeButton("No", null)
                .show();
    }

    // ------------------------------------------------------------------ invio

    private void onSend() {
        if (busy) {
            if (cancel != null) cancel.cancel();
            return;
        }
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return;
        final AutoBrain.Choice choice = AutoBrain.choose(store.brains(), store.activeBrain(), store.autoSwitch(),
                Net.isOnline(this), b -> ModelStore.isReady(this, b.model));
        input.setText("");
        if (chat.isEmpty()) list.removeAllViews(); // toglie il testo di benvenuto
        if (SQUAD.equals(modeId)) {
            startSquad(text);
            return;
        }
        if (PIPELINE.equals(modeId)) {
            startPipeline(text, choice);
            return;
        }
        List<Model.Brain> team = store.parallel() && Net.isOnline(this) ? parallelBrains() : new ArrayList<>();
        if (team.size() >= 2) startParallel(text, team);
        else startChat(text, choice);
    }

    private Model.Agent currentAgent() {
        for (Model.Agent a : agents) if (a.id.equals(modeId)) return a;
        return agents.isEmpty() ? Presets.agentSalvatore() : agents.get(0);
    }

    private void startChat(String text, final AutoBrain.Choice choice) {
        Model.Agent ag = currentAgent();
        Model.Entry u = new Model.Entry("", "user", text);
        chat.add(u);
        addBubble(u);
        final String who = choice.primary.isLocal() ? ag.name + " · nel tablet" : ag.name;
        final Model.Entry reply = new Model.Entry(who, "assistant", "");
        chat.add(reply);
        liveEntry = reply;
        liveView = addBubble(reply);
        final List<Model.Msg> msgs = Model.buildMessages(systemFor(ag, choice.primary.isLocal()), chat, 20);
        pendingQ = text;
        pendingReplies.clear();
        pendingReplies.add(reply);
        setBusy(true);
        scrollDown();

        final OpenAiClient.Cancel c = new OpenAiClient.Cancel();
        cancel = c;
        new Thread(() -> {
            String err = null;
            try {
                new SmartBrain(choice.primary, choice.fallback, c, n -> ui.post(() -> addInfo(n)))
                        .chat(msgs, d -> ui.post(() -> appendLive(d)));
            } catch (OpenAiClient.ChatException e) {
                err = e.getMessage();
            } catch (RuntimeException e) {
                err = "Errore inatteso: " + e;
            }
            final String fErr = err;
            ui.post(() -> finishChat(fErr));
        }).start();
    }

    private void startPipeline(String text, final AutoBrain.Choice choice) {
        Model.Entry u = new Model.Entry("Tu", "user", text);
        chat.add(u);
        addBubble(u);
        final String ing = store.promptOf(Presets.ID_INGEGNERE);
        final String rev = store.promptOf(Presets.ID_REVISORE);
        setBusy(true);
        scrollDown();

        final OpenAiClient.Cancel c = new OpenAiClient.Cancel();
        cancel = c;
        new Thread(() -> {
            String err = null;
            Pipeline.Result res = null;
            Pipeline.Events ev = new Pipeline.Events() {
                @Override
                public void begin(String label) {
                    ui.post(() -> beginLive(label));
                }

                @Override
                public void delta(String t) {
                    ui.post(() -> appendLive(t));
                }

                @Override
                public void end(String full) {
                    ui.post(() -> {
                        liveEntry = null;
                        liveView = null;
                        store.saveChat(modeId, chat);
                    });
                }

                @Override
                public boolean cancelled() {
                    return c.cancelled;
                }
            };
            try {
                res = Pipeline.run(new SmartBrain(choice.primary, choice.fallback, c, n -> ui.post(() -> addInfo(n))),
                        ing, rev, text, ev);
            } catch (OpenAiClient.ChatException e) {
                err = e.getMessage();
            } catch (RuntimeException e) {
                err = "Errore inatteso: " + e;
            }
            final String fErr = err;
            final Pipeline.Result fRes = res;
            ui.post(() -> finishPipeline(fRes, fErr, c.cancelled));
        }).start();
    }

    private void beginLive(String label) {
        liveEntry = new Model.Entry(label, "assistant", "");
        chat.add(liveEntry);
        liveView = addBubble(liveEntry);
        scrollDown();
    }

    private void appendLive(String d) {
        if (liveEntry == null || liveView == null) return;
        liveEntry.text = liveEntry.text + d;
        liveView.setText(shown(liveEntry));
        scrollDown();
    }

    /** Se la risposta in corso è rimasta vuota, toglie la bolla vuota. */
    private void dropEmptyLive() {
        if (liveEntry != null && liveEntry.text.isEmpty()) {
            chat.remove(liveEntry);
            if (liveView != null && liveView.getParent() instanceof View) {
                list.removeView((View) liveView.getParent());
            }
        }
        liveEntry = null;
        liveView = null;
    }

    private void finishChat(String err) {
        dropEmptyLive();
        if (err != null) addNotice(err);
        setBusy(false);
        store.saveChat(modeId, chat);
        logProject();
    }

    // ------------------------------------------------------------------ progetto condiviso

    private String systemFor(Model.Agent ag, boolean local) {
        String proj = store.project().trim();
        if (proj.isEmpty() || proj.equals(Project.TEMPLATE.trim())) return ag.prompt;
        return ag.prompt + "\n\nScheda del progetto, condivisa con le altre AI che lavorano allo stesso progetto. "
                + "Tienine conto e non contraddire le decisioni già prese:\n" + Project.forPrompt(proj, local ? 1200 : 3500);
    }

    /** I cervelli che rispondono insieme: online, con la chiave inserita. */
    private List<Model.Brain> parallelBrains() {
        List<Model.Brain> out = new ArrayList<>();
        for (Model.Brain b : store.brains()) {
            if (b.isLocal() || b.url == null || b.url.trim().isEmpty()) continue;
            if (b.key == null || b.key.trim().isEmpty()) continue;
            if (out.size() < 4) out.add(b);
        }
        return out;
    }

    private void startParallel(String text, List<Model.Brain> team) {
        Model.Agent ag = currentAgent();
        Model.Entry u = new Model.Entry("", "user", text);
        chat.add(u);
        addBubble(u);
        final List<Model.Msg> msgs = Model.buildMessages(systemFor(ag, false), chat, 20);
        pendingQ = text;
        pendingReplies.clear();
        setBusy(true);

        final List<OpenAiClient.Cancel> subs = new ArrayList<>();
        final OpenAiClient.Cancel master = new OpenAiClient.Cancel();
        cancel = master;
        final AtomicInteger left = new AtomicInteger(team.size());
        final List<String> errors = new ArrayList<>();
        for (final Model.Brain b : team) {
            final Model.Entry e = new Model.Entry(ag.name + " · " + b.name, "assistant", "");
            chat.add(e);
            pendingReplies.add(e);
            final TextView v = addBubble(e);
            final OpenAiClient.Cancel c = new OpenAiClient.Cancel();
            subs.add(c);
            new Thread(() -> {
                String err = null;
                try {
                    OpenAiClient.chat(b, msgs, d -> ui.post(() -> {
                        e.text = e.text + d;
                        v.setText(shown(e));
                        scrollDown();
                    }), c);
                } catch (OpenAiClient.ChatException ex) {
                    err = ex.getMessage();
                } catch (RuntimeException ex) {
                    err = "Errore inatteso: " + ex;
                }
                final String fErr = err;
                ui.post(() -> {
                    if (fErr != null) errors.add(b.name + ": " + fErr);
                    if (e.text.isEmpty()) {
                        chat.remove(e);
                        pendingReplies.remove(e);
                        if (v.getParent() instanceof View) list.removeView((View) v.getParent());
                    }
                    if (left.decrementAndGet() == 0) {
                        for (String m : errors) addNotice(m);
                        setBusy(false);
                        store.saveChat(modeId, chat);
                        logProject();
                    }
                });
            }).start();
        }
        master.setOnCancel(() -> {
            for (OpenAiClient.Cancel c : subs) c.cancel();
        });
        scrollDown();
    }

    private void startSquad(final String text) {
        List<Model.Brain> all = parallelBrains();
        String lid = store.leaderId();
        Model.Brain lead = null;
        for (Model.Brain b : all) if (b.id.equals(lid)) lead = b;
        if (lead == null) {
            Model.Brain act = store.activeBrain();
            for (Model.Brain b : all) if (b.id.equals(act.id)) lead = b;
        }
        if (lead == null && !all.isEmpty()) lead = all.get(0);
        final List<Model.Brain> helpers = new ArrayList<>();
        for (Model.Brain b : all) if (lead != null && !b.id.equals(lead.id)) helpers.add(b);
        Model.Entry u = new Model.Entry("Tu", "user", text);
        chat.add(u);
        addBubble(u);
        if (lead == null || helpers.isEmpty() || !Net.isOnline(this)) {
            addNotice("Per la squadra servono internet e almeno 2 cervelli online con la chiave (un capo e un collaboratore). Aggiungili nelle Impostazioni.");
            store.saveChat(modeId, chat);
            return;
        }
        final Model.Brain leader = lead;
        final OpenAiClient.Cancel master = new OpenAiClient.Cancel();
        cancel = master;
        final List<OpenAiClient.Cancel> subs = new ArrayList<>();
        final List<Team.Worker> workers = new ArrayList<>();
        for (Model.Brain h : helpers) {
            final OpenAiClient.Cancel c = new OpenAiClient.Cancel();
            subs.add(c);
            final Model.Brain hb = h;
            workers.add(new Team.Worker(h.name, (m, sink) -> OpenAiClient.chat(hb, m, sink, c)));
        }
        final OpenAiClient.Cancel lc = new OpenAiClient.Cancel();
        subs.add(lc);
        master.setOnCancel(() -> {
            for (OpenAiClient.Cancel c : subs) c.cancel();
        });
        final String ctxProj = Project.forPrompt(store.project().equals(Project.TEMPLATE) ? "" : store.project(), 3000);
        pendingQ = text;
        pendingReplies.clear();
        setBusy(true);
        scrollDown();

        final java.util.Map<String, Model.Entry> entries = new java.util.concurrent.ConcurrentHashMap<>();
        final java.util.Map<String, TextView> views = new java.util.concurrent.ConcurrentHashMap<>();
        final Team.Events ev = new Team.Events() {
            @Override
            public void begin(String key, String label) {
                ui.post(() -> {
                    Model.Entry e = new Model.Entry(label, "assistant", "");
                    entries.put(key, e);
                    chat.add(e);
                    views.put(key, addBubble(e));
                    if (key.equals("final")) pendingReplies.add(e);
                    scrollDown();
                });
            }

            @Override
            public void delta(String key, String t) {
                ui.post(() -> {
                    Model.Entry e = entries.get(key);
                    TextView v = views.get(key);
                    if (e == null || v == null) return;
                    e.text = e.text + t;
                    v.setText(shown(e));
                    scrollDown();
                });
            }

            @Override
            public void end(String key) {}

            @Override
            public boolean cancelled() {
                return master.cancelled;
            }
        };
        new Thread(() -> {
            String err = null;
            try {
                Team.run((m, sink) -> OpenAiClient.chat(leader, m, sink, lc), workers, text, ctxProj, ev);
            } catch (OpenAiClient.ChatException e) {
                err = e.getMessage();
            } catch (RuntimeException e) {
                err = "Errore inatteso: " + e;
            }
            final String fErr = err;
            ui.post(() -> {
                for (java.util.Iterator<Model.Entry> it = chat.iterator(); it.hasNext(); ) {
                    Model.Entry e = it.next();
                    if (e.text.isEmpty() && "assistant".equals(e.role)) it.remove();
                }
                render();
                if (fErr != null) addNotice(fErr);
                setBusy(false);
                store.saveChat(modeId, chat);
                logProject();
            });
        }).start();
    }

    /** Scrive nel registro del progetto cosa è stato chiesto e risposto, e (se acceso) lo salva su GitHub. */
    private void logProject() {
        if (pendingQ.isEmpty()) return;
        final String q = pendingQ;
        final List<String[]> rows = new ArrayList<>();
        for (Model.Entry e : pendingReplies) {
            if (e.text != null && !e.text.isEmpty()) rows.add(new String[]{e.label, e.text});
        }
        pendingQ = "";
        pendingReplies.clear();
        if (rows.isEmpty()) return;
        final String when = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ITALY).format(new java.util.Date());
        projectWork.execute(() -> {
            String doc = store.project();
            for (String[] r : rows) doc = Project.appendLog(doc, Project.logLine(when, r[0], q, Fmt.plain(r[1])));
            store.setProject(doc);
            String token = store.ghToken();
            if (!store.ghAuto() || token.isEmpty()) return;
            try {
                String res = new GitHubSync().sync(store.ghRepo(), store.ghPath(), token, store.projectBase(), doc, true);
                store.setProject(res);
                store.setProjectBase(res);
            } catch (OpenAiClient.ChatException e) {
                final String m = "GitHub: " + e.getMessage();
                ui.post(() -> addInfo(m));
            } catch (RuntimeException e) {
                ui.post(() -> addInfo("GitHub: errore inatteso."));
            }
        });
    }

    private void finishPipeline(Pipeline.Result res, String err, boolean stopped) {
        dropEmptyLive();
        if (err != null) {
            addNotice(err);
        } else if (res != null) {
            String s;
            if (stopped) s = "Fermato.";
            else if (res.approved) s = "Il Revisore ha approvato al giro " + res.rounds + ".";
            else s = "Dopo " + res.rounds + " giri il Revisore non ha ancora approvato. Qui sopra c'è l'ultima versione dell'Ingegnere.";
            Model.Entry e = new Model.Entry("Esito", "assistant", s);
            chat.add(e);
            addBubble(e);
            scrollDown();
        }
        setBusy(false);
        store.saveChat(modeId, chat);
    }
}
