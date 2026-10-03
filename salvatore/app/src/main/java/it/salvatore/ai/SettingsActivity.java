package it.salvatore.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/** Impostazioni: quale cervello usare e come sono fatti gli agenti. Si salva da solo. */
public class SettingsActivity extends Activity {

    private final Handler ui = new Handler(Looper.getMainLooper());
    private Store store;
    private Ui th;

    private List<Model.Brain> brains;
    private Model.Brain shownBrain;
    private final List<String> brainNames = new ArrayList<>();
    private ArrayAdapter<String> brainAd;
    private Spinner brainSpinner;
    private EditText bName, bUrl, bKey, bModel;
    private LinearLayout remoteBox, localBox, modelsBox;
    private EditText pText, gToken, gRepo, gPath;
    private TextView localStatus;
    private Button btnDownload, btnPick, btnDelete;
    private volatile boolean busyModel = false;
    private boolean filling = false;
    private boolean dName, dUrl, dKey, dModel;
    private OpenAiClient.Cancel modelCancel;
    private static final int REQ_PICK = 77;

    private List<Model.Agent> agents;
    private Model.Agent shownAgent;
    private final List<String> agentNames = new ArrayList<>();
    private ArrayAdapter<String> agentAd;
    private Spinner agentSpinner;
    private EditText aName, aPrompt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new Store(this);
        th = new Ui(this);
        LocalBrain.install(this);

        brains = store.brains();
        String activeId = store.activeBrain().id;
        shownBrain = brains.get(0);
        for (Model.Brain b : brains) if (b.id.equals(activeId)) shownBrain = b;
        agents = store.agents();
        shownAgent = agents.get(0);

        buildUi();
        fillBrain();
        fillAgent();
    }

    @Override
    protected void onPause() {
        super.onPause();
        persist();
    }

    private void persist() {
        commitBrain();
        commitAgent();
        store.saveBrains(brains);
        store.setActiveBrainId(shownBrain.id);
        store.saveAgents(agents);
        persistProject();
    }

    private void persistProject() {
        if (pText == null) return;
        store.setProject(pText.getText().toString());
        store.setGh(gToken.getText().toString().trim(), gRepo.getText().toString().trim(), gPath.getText().toString().trim());
    }

    // ------------------------------------------------------------------ schermata

    private LinearLayout card(LinearLayout parent) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(th.rounded(th.card, 16));
        c.setPadding(th.dp(16), th.dp(14), th.dp(16), th.dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, th.dp(16));
        parent.addView(c, lp);
        return c;
    }

    private void labeled(LinearLayout parent, String label, EditText f) {
        TextView l = th.label(label, 13, th.sub, false);
        l.setPadding(0, th.dp(10), 0, th.dp(4));
        parent.addView(l);
        parent.addView(f, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private LinearLayout buttons(Button... bs) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (Button b : bs) row.addView(b);
        return row;
    }

    private void buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(th.bg);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(th.dp(16), th.dp(16), th.dp(16), th.dp(24));
        sv.addView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(android.view.Gravity.CENTER_VERTICAL);
        top.addView(th.label("Impostazioni", 24, th.text, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(th.button("Fatto", v -> finish()));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.setMargins(0, 0, 0, th.dp(12));
        root.addView(top, tlp);

        // ---- Cervello ----
        LinearLayout bc = card(root);
        bc.addView(th.label("Cervello", 20, th.text, true));
        TextView bh = th.label("È il motore che pensa. Può stare sul tablet, su un computer di casa o su un server online. "
                + "Quello scelto qui sotto è quello in uso.", 14, th.sub, false);
        bh.setPadding(0, th.dp(4), 0, th.dp(8));
        bc.addView(bh);

        brainSpinner = new Spinner(this);
        brainAd = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, brainNames);
        brainAd.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        for (Model.Brain b : brains) brainNames.add(brainLabel(b));
        brainSpinner.setAdapter(brainAd);
        brainSpinner.setSelection(brains.indexOf(shownBrain));
        brainSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (pos < 0 || pos >= brains.size()) return;
                Model.Brain target = brains.get(pos);
                if (target == shownBrain) return;
                commitBrain();
                shownBrain = target;
                fillBrain();
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {}
        });
        bc.addView(brainSpinner);
        bc.addView(buttons(
                th.button("Aggiungi cervello", v -> onAddBrain()),
                th.button("Elimina questo", v -> onDeleteBrain())));

        bName = th.field("Nome", false);
        bUrl = th.field("http://127.0.0.1:8080/v1", false);
        bUrl.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        bKey = th.field("Lascia vuoto se non serve", false);
        bKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        bModel = th.field("Nome del modello", false);
        bModel.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        watch(bName, 0);
        watch(bUrl, 1);
        watch(bKey, 2);
        watch(bModel, 3);
        labeled(bc, "Nome (lo scegli tu)", bName);

        remoteBox = new LinearLayout(this);
        remoteBox.setOrientation(LinearLayout.VERTICAL);
        labeled(remoteBox, "Indirizzo", bUrl);
        labeled(remoteBox, "Chiave di accesso", bKey);
        CheckBox show = new CheckBox(this);
        show.setText("Mostra la chiave");
        show.setTextColor(th.sub);
        show.setOnCheckedChangeListener((b, on) -> {
            int t = on ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_TEXT_VARIATION_PASSWORD;
            bKey.setInputType(InputType.TYPE_CLASS_TEXT | t);
        });
        remoteBox.addView(show);
        remoteBox.addView(th.button("Prendi la chiave (apre il sito)", v -> onKeyLink()));
        bc.addView(remoteBox);

        labeled(bc, "Modello (nome del file, se è dentro il tablet)", bModel);
        modelsBox = new LinearLayout(this);
        modelsBox.setOrientation(LinearLayout.VERTICAL);
        modelsBox.addView(th.button("Cerca modelli", v -> onFindModels()));
        bc.addView(modelsBox);

        localBox = new LinearLayout(this);
        localBox.setOrientation(LinearLayout.VERTICAL);
        localBox.setPadding(0, th.dp(12), 0, 0);
        localStatus = th.label("", 14, th.text, false);
        localBox.addView(localStatus);
        TextView lh = th.label("Questo cervello gira dentro il tablet, anche senza internet. Con 4 GB di memoria è un modello piccolo: "
                + "risponde in modo più semplice e più lento di quelli online. La prima risposta dopo l'avvio è la più lenta. "
                + "Durante lo scaricamento tieni lo schermo acceso.", 13, th.sub, false);
        lh.setPadding(0, th.dp(4), 0, th.dp(4));
        localBox.addView(lh);
        btnDownload = th.button("Scarica o scegli un modello…", v -> onChooseModel());
        btnPick = th.button("Scegli un file dal tablet o da Drive", v -> onPick());
        btnDelete = th.button("Elimina il modello dal tablet", v -> onDeleteModel());
        localBox.addView(btnDownload);
        localBox.addView(btnPick);
        localBox.addView(btnDelete);
        bc.addView(localBox);

        CheckBox auto = new CheckBox(this);
        auto.setText("Scegli da solo: online quando c'è internet, dentro il tablet quando manca (o quando l'online non risponde)");
        auto.setTextColor(th.text);
        auto.setChecked(store.autoSwitch());
        auto.setOnCheckedChangeListener((b, on) -> store.setAutoSwitch(on));
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        al.topMargin = th.dp(16);
        bc.addView(auto, al);
        TextView ah2 = th.label("Per far funzionare il passaggio servono due cervelli: uno online (con la sua chiave) e uno dentro il tablet "
                + "con il modello già scaricato.", 13, th.sub, false);
        ah2.setPadding(th.dp(32), 0, 0, 0);
        bc.addView(ah2);

        Button test = th.button("Prova il collegamento", v -> onTest());
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.topMargin = th.dp(12);
        bc.addView(test, tl);

        TextView ex = th.label("Esempi:\n"
                + "• Sul tablet: un programma che fa girare il modello e offre un indirizzo come http://127.0.0.1:8080/v1\n"
                + "• Online (Gemini, Groq, OpenRouter…): serve una chiave che ti dà il servizio.\n"
                + "• Tuo server: l'indirizzo del server, di solito finisce con /v1.", 13, th.sub, false);
        ex.setPadding(0, th.dp(12), 0, 0);
        bc.addView(ex);

        // ---- Progetto e squadra ----
        LinearLayout pc = card(root);
        pc.addView(th.label("Progetto e squadra", 20, th.text, true));
        TextView ph = th.label("La scheda del progetto è un testo che tutti i cervelli leggono prima di rispondere: così lavorano sulla stessa cosa. "
                + "Salvatore ci scrive da solo un registro di quello che è stato fatto, e la tiene allineata su GitHub, dove la leggo anche io.",
                14, th.sub, false);
        ph.setPadding(0, th.dp(4), 0, th.dp(8));
        pc.addView(ph);

        CheckBox par = new CheckBox(this);
        par.setText("Lavora in parallelo: ogni domanda va a tutti i cervelli online che hanno la chiave, e ognuno risponde");
        par.setTextColor(th.text);
        par.setChecked(store.parallel());
        par.setOnCheckedChangeListener((b, on) -> store.setParallel(on));
        pc.addView(par);
        TextView pph = th.label("Attenzione: ogni cervello usa le sue richieste gratis, quindi si consumano più in fretta. "
                + "Con 2 o 3 cervelli va bene.", 13, th.sub, false);
        pph.setPadding(th.dp(32), 0, 0, th.dp(8));
        pc.addView(pph);

        pText = th.field("Scrivi qui l'obiettivo e le decisioni del progetto", true);
        pText.setMinLines(6);
        pText.setMaxLines(16);
        pText.setText(store.project().isEmpty() ? Project.TEMPLATE : store.project());
        labeled(pc, "Scheda del progetto", pText);

        gRepo = th.field("nome-utente/nome-deposito", false);
        gRepo.setText(store.ghRepo());
        gPath = th.field("progetto/SALVATORE.md", false);
        gPath.setText(store.ghPath());
        gToken = th.field("Chiave GitHub (inizia con github_pat_ o ghp_)", false);
        gToken.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        gToken.setText(store.ghToken());
        labeled(pc, "Deposito GitHub", gRepo);
        labeled(pc, "File del progetto", gPath);
        labeled(pc, "Chiave GitHub", gToken);

        CheckBox gauto = new CheckBox(this);
        gauto.setText("Salva da solo su GitHub dopo ogni risposta");
        gauto.setTextColor(th.text);
        gauto.setChecked(store.ghAuto());
        gauto.setOnCheckedChangeListener((b, on) -> store.setGhAuto(on));
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gl.topMargin = th.dp(8);
        pc.addView(gauto, gl);
        pc.addView(buttons(
                th.button("Salva su GitHub", v -> onGitHub(true)),
                th.button("Leggi da GitHub", v -> onGitHub(false))));
        TextView gh2 = th.label("La chiave GitHub si crea su github.com → Settings → Developer settings → Fine-grained tokens, "
                + "scegliendo solo questo deposito e il permesso \"Contents: Read and write\". Resta solo su questo tablet.",
                13, th.sub, false);
        gh2.setPadding(0, th.dp(8), 0, 0);
        pc.addView(gh2);

        // ---- Agenti ----
        LinearLayout ac = card(root);
        ac.addView(th.label("Agenti", 20, th.text, true));
        TextView ah = th.label("Ogni agente ha un nome e delle istruzioni che ne decidono il ruolo e il modo di rispondere. "
                + "Funzionano con qualunque cervello. Ingegnere e Revisore sono usati insieme nella modalità a giri.",
                14, th.sub, false);
        ah.setPadding(0, th.dp(4), 0, th.dp(8));
        ac.addView(ah);

        agentSpinner = new Spinner(this);
        agentAd = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, agentNames);
        agentAd.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        for (Model.Agent a : agents) agentNames.add(agentLabel(a));
        agentSpinner.setAdapter(agentAd);
        agentSpinner.setSelection(0);
        agentSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (pos < 0 || pos >= agents.size()) return;
                Model.Agent target = agents.get(pos);
                if (target == shownAgent) return;
                commitAgent();
                shownAgent = target;
                fillAgent();
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {}
        });
        ac.addView(agentSpinner);
        ac.addView(buttons(
                th.button("Aggiungi agente", v -> onAddAgent()),
                th.button("Elimina questo", v -> onDeleteAgent())));

        aName = th.field("Nome dell'agente", false);
        aPrompt = th.field("Istruzioni", true);
        aPrompt.setMinLines(6);
        aPrompt.setMaxLines(14);
        labeled(ac, "Nome", aName);
        labeled(ac, "Istruzioni", aPrompt);

        Button reset = th.button("Ripristina Salvatore, Ingegnere e Revisore", v -> onResetBase());
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rl.topMargin = th.dp(12);
        ac.addView(reset, rl);

        TextView about = th.label("Salvatore · versione 1.4\nLe chat, i cervelli e le chiavi restano solo su questo tablet.",
                13, th.sub, false);
        about.setPadding(th.dp(4), th.dp(4), 0, 0);
        root.addView(about);

        setContentView(sv);
    }

    // ------------------------------------------------------------------ cervelli

    private static String brainLabel(Model.Brain b) {
        return b.name.isEmpty() ? "(senza nome)" : b.name;
    }

    private static String agentLabel(Model.Agent a) {
        return a.name.isEmpty() ? "(senza nome)" : a.name;
    }

    private void fillBrain() {
        filling = true;
        bName.setText(shownBrain.name);
        bUrl.setText(shownBrain.url);
        bKey.setText(shownBrain.key);
        bModel.setText(shownBrain.model);
        filling = false;
        dName = dUrl = dKey = dModel = false;
        updateBrainLayout();
    }

    /** Segna un campo come "toccato dall'utente": solo quelli si salvano, così nulla si sovrascrive per sbaglio. */
    private void watch(EditText e, final int which) {
        e.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (filling) return;
                if (which == 0) dName = true;
                else if (which == 1) dUrl = true;
                else if (which == 2) dKey = true;
                else dModel = true;
            }
        });
    }

    /** Mostra solo i campi che servono: indirizzo e chiave per i cervelli online, modello per quello nel tablet. */
    private void updateBrainLayout() {
        boolean local = shownBrain != null && shownBrain.isLocal();
        remoteBox.setVisibility(local ? View.GONE : View.VISIBLE);
        localBox.setVisibility(local ? View.VISIBLE : View.GONE);
        modelsBox.setVisibility(local ? View.GONE : View.VISIBLE);
        if (local) refreshLocalStatus(null);
    }

    private void commitBrain() {
        if (shownBrain == null) return;
        if (dName) shownBrain.name = bName.getText().toString().trim();
        if (!shownBrain.isLocal()) {
            if (dUrl) shownBrain.url = bUrl.getText().toString().trim();
            if (dKey) shownBrain.key = bKey.getText().toString().trim();
        }
        if (dModel) shownBrain.model = bModel.getText().toString().trim();
        dName = dUrl = dKey = dModel = false;
        int i = brains.indexOf(shownBrain);
        if (i >= 0 && i < brainNames.size()) {
            brainNames.set(i, brainLabel(shownBrain));
            brainAd.notifyDataSetChanged();
        }
    }

    private void onAddBrain() {
        final String[] labels = new String[Presets.BRAINS.length];
        for (int i = 0; i < labels.length; i++) labels[i] = Presets.BRAINS[i][0];
        new AlertDialog.Builder(this)
                .setTitle("Che tipo di cervello vuoi aggiungere?")
                .setItems(labels, (d, which) -> {
                    commitBrain();
                    String[] p = Presets.BRAINS[which];
                    Model.Brain nb = new Model.Brain(Presets.newId(), p[0], p[1], "", p[2]);
                    brains.add(nb);
                    brainNames.add(brainLabel(nb));
                    brainAd.notifyDataSetChanged();
                    shownBrain = nb;
                    fillBrain();
                    brainSpinner.setSelection(brains.size() - 1);
                })
                .setNegativeButton("Annulla", null)
                .show();
    }

    private void onDeleteBrain() {
        if (brains.size() <= 1) {
            Toast.makeText(this, "Deve restare almeno un cervello.", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Elimina cervello")
                .setMessage("Elimino \"" + brainLabel(shownBrain) + "\"?")
                .setPositiveButton("Elimina", (d, w) -> {
                    int i = brains.indexOf(shownBrain);
                    if (i < 0) return;
                    brains.remove(i);
                    brainNames.remove(i);
                    int ni = Math.min(i, brains.size() - 1);
                    shownBrain = brains.get(ni);
                    brainAd.notifyDataSetChanged();
                    fillBrain();
                    brainSpinner.setSelection(ni);
                })
                .setNegativeButton("Annulla", null)
                .show();
    }

    private void onGitHub(final boolean write) {
        persist();
        final String token = store.ghToken(), repo = store.ghRepo(), path = store.ghPath();
        final String local = store.project();
        final String base = store.projectBase();
        Toast.makeText(this, "Parlo con GitHub…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String res = null, err = null;
            try {
                res = new GitHubSync().sync(repo, path, token, base, local, write);
            } catch (OpenAiClient.ChatException e) {
                err = e.getMessage();
            } catch (RuntimeException e) {
                err = "Errore inatteso: " + e;
            }
            final String fr = res, fe = err;
            ui.post(() -> {
                if (isFinishing()) return;
                if (fr != null) {
                    store.setProject(fr);
                    store.setProjectBase(fr);
                    pText.setText(fr);
                }
                new AlertDialog.Builder(this)
                        .setTitle("GitHub")
                        .setMessage(fe != null ? fe : (write ? "Fatto: la scheda è salvata su GitHub." : "Fatto: ho letto la scheda da GitHub."))
                        .setPositiveButton("OK", null)
                        .show();
            });
        }).start();
    }

    private void onKeyLink() {
        String link = Presets.keyLink(bUrl.getText().toString());
        if (link.isEmpty()) {
            Toast.makeText(this, "Per questo servizio non conosco la pagina. Cercala sul loro sito (\"API keys\").", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)));
            Toast.makeText(this, "Crea la chiave, copiala e torna qui a incollarla.", Toast.LENGTH_LONG).show();
        } catch (RuntimeException e) {
            Toast.makeText(this, "Non riesco ad aprire il browser. Vai a: " + link, Toast.LENGTH_LONG).show();
        }
    }

    private void onFindModels() {
        final Model.Brain b = new Model.Brain(shownBrain.id, shownBrain.name,
                bUrl.getText().toString().trim(), bKey.getText().toString().trim(), "");
        Toast.makeText(this, "Cerco i modelli…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            List<String> found = null;
            String err = null;
            try {
                found = OpenAiClient.listModels(b);
            } catch (OpenAiClient.ChatException e) {
                err = e.getMessage();
            } catch (RuntimeException e) {
                err = "Errore inatteso: " + e;
            }
            final List<String> fl = found;
            final String fe = err;
            ui.post(() -> {
                if (isFinishing()) return;
                if (fl == null) {
                    new AlertDialog.Builder(this).setTitle("Cerca modelli").setMessage(fe)
                            .setPositiveButton("OK", null).show();
                    return;
                }
                final String[] names = fl.toArray(new String[0]);
                new AlertDialog.Builder(this)
                        .setTitle("Scegli il modello (" + names.length + ")")
                        .setItems(names, (d, which) -> bModel.setText(names[which]))
                        .setNegativeButton("Annulla", null)
                        .show();
            });
        }).start();
    }

    private void onTest() {
        commitBrain();
        final Model.Brain b = new Model.Brain(shownBrain.id, shownBrain.name, shownBrain.url, shownBrain.key, shownBrain.model);
        Toast.makeText(this, "Provo a collegarmi…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String msg;
            try {
                List<Model.Msg> m = new ArrayList<>();
                m.add(new Model.Msg("user", "Rispondi con una sola parola: ok"));
                String r = OpenAiClient.chat(b, m, null, new OpenAiClient.Cancel()).trim();
                if (r.length() > 200) r = r.substring(0, 200) + "…";
                msg = "Funziona! Il cervello ha risposto: " + r;
            } catch (OpenAiClient.ChatException e) {
                msg = e.getMessage();
            } catch (RuntimeException e) {
                msg = "Errore inatteso: " + e;
            }
            final String fMsg = msg;
            ui.post(() -> {
                if (isFinishing()) return;
                new AlertDialog.Builder(this)
                        .setTitle("Prova del collegamento")
                        .setMessage(fMsg)
                        .setPositiveButton("OK", null)
                        .show();
            });
        }).start();
    }

    // ------------------------------------------------------------------ modello dentro il tablet

    private String modelName() {
        String n = ModelStore.safeName(bModel.getText().toString());
        return n.isEmpty() ? Presets.LOCAL_FILE : n;
    }

    private void refreshLocalStatus(String extra) {
        String name = modelName();
        String s;
        if (ModelStore.isReady(this, name)) {
            s = "Modello pronto: " + name + " (" + ModelStore.mb(ModelStore.file(this, name).length()) + ")";
        } else {
            s = "Modello non ancora sul tablet: " + name;
        }
        if (extra != null) s = s + "\n" + extra;
        localStatus.setText(s);
        btnDownload.setText(busyModel ? "Annulla" : "Scarica o scegli un modello…");
        btnPick.setEnabled(!busyModel);
        btnDelete.setEnabled(!busyModel);
    }

    private void onChooseModel() {
        if (busyModel) {
            if (modelCancel != null) modelCancel.cancel();
            return;
        }
        final String[] items = new String[Presets.LOCAL_MODELS.length];
        for (int i = 0; i < items.length; i++) {
            String[] m = Presets.LOCAL_MODELS[i];
            boolean have = ModelStore.isReady(this, m[1]);
            items[i] = m[0] + " (" + m[3] + " MB)" + (have ? " ✓ già nel tablet" : "");
        }
        new AlertDialog.Builder(this)
                .setTitle("Quale modello?")
                .setItems(items, (d, which) -> {
                    String[] m = Presets.LOCAL_MODELS[which];
                    if (ModelStore.isReady(this, m[1])) {
                        bModel.setText(m[1]);
                        commitBrain();
                        refreshLocalStatus("Modello scelto.");
                    } else {
                        onDownload(m[1], m[2]);
                    }
                })
                .setNegativeButton("Annulla", null)
                .show();
    }

    private void onDownload(final String file, final String url) {
        bModel.setText(file);
        commitBrain();
        busyModel = true;
        final OpenAiClient.Cancel c = new OpenAiClient.Cancel();
        modelCancel = c;
        refreshLocalStatus("Scaricamento in corso…");
        new Thread(() -> {
            String result;
            try {
                ModelStore.download(getApplicationContext(), url, file, (done, total) -> {
                    final String t = total > 0
                            ? "Scaricamento: " + ModelStore.mb(done) + " di " + ModelStore.mb(total) + " (" + (done * 100 / total) + "%)"
                            : "Scaricamento: " + ModelStore.mb(done);
                    ui.post(() -> {
                        if (!isFinishing() && busyModel) refreshLocalStatus(t);
                    });
                }, c);
                result = "Fatto! Il modello è sul tablet.";
            } catch (java.io.IOException e) {
                result = "Non riuscito: " + e.getMessage();
            } catch (RuntimeException e) {
                result = "Non riuscito: " + e;
            }
            final String fr = result;
            ui.post(() -> {
                busyModel = false;
                modelCancel = null;
                if (!isFinishing()) refreshLocalStatus(fr);
            });
        }).start();
    }

    private void onPick() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(i, REQ_PICK);
        } catch (RuntimeException e) {
            Toast.makeText(this, "Non riesco ad aprire la scelta dei file.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        final String name = ModelStore.displayName(this, uri);
        if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".litertlm")) {
            new AlertDialog.Builder(this)
                    .setTitle("File non adatto")
                    .setMessage("Il file \"" + name + "\" non è un modello per Salvatore. Serve un file che finisce con .litertlm.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        busyModel = true;
        final OpenAiClient.Cancel c = new OpenAiClient.Cancel();
        modelCancel = c;
        refreshLocalStatus("Copio il file nel tablet…");
        new Thread(() -> {
            String result;
            String saved = null;
            try {
                saved = ModelStore.importFrom(getApplicationContext(), uri, name, (done, total) -> {
                    final String t = total > 0
                            ? "Copia: " + ModelStore.mb(done) + " di " + ModelStore.mb(total)
                            : "Copia: " + ModelStore.mb(done);
                    ui.post(() -> {
                        if (!isFinishing() && busyModel) refreshLocalStatus(t);
                    });
                }, c);
                result = "Fatto! File copiato nel tablet.";
            } catch (java.io.IOException e) {
                result = "Non riuscito: " + e.getMessage();
            } catch (RuntimeException e) {
                result = "Non riuscito: " + e;
            }
            final String fr = result;
            final String fs = saved;
            ui.post(() -> {
                busyModel = false;
                modelCancel = null;
                if (isFinishing()) return;
                if (fs != null) bModel.setText(fs);
                commitBrain();
                refreshLocalStatus(fr);
            });
        }).start();
    }

    private void onDeleteModel() {
        final String name = modelName();
        if (!ModelStore.isReady(this, name)) {
            Toast.makeText(this, "Non c'è nessun modello da eliminare.", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Elimina il modello")
                .setMessage("Elimino \"" + name + "\" dal tablet? Libera spazio, e lo puoi riscaricare quando vuoi.")
                .setPositiveButton("Elimina", (d, w) -> {
                    LocalBrain.release();
                    ModelStore.delete(this, name);
                    refreshLocalStatus("Modello eliminato.");
                })
                .setNegativeButton("Annulla", null)
                .show();
    }

    // ------------------------------------------------------------------ agenti

    private void fillAgent() {
        aName.setText(shownAgent.name);
        aPrompt.setText(shownAgent.prompt);
    }

    private void commitAgent() {
        if (shownAgent == null) return;
        shownAgent.name = aName.getText().toString().trim();
        shownAgent.prompt = aPrompt.getText().toString().trim();
        int i = agents.indexOf(shownAgent);
        if (i >= 0 && i < agentNames.size()) {
            agentNames.set(i, agentLabel(shownAgent));
            agentAd.notifyDataSetChanged();
        }
    }

    private void onAddAgent() {
        commitAgent();
        Model.Agent na = new Model.Agent(Presets.newId(), "Nuovo agente",
                "Sei un assistente. Rispondi in italiano, in modo semplice e breve.");
        agents.add(na);
        agentNames.add(agentLabel(na));
        agentAd.notifyDataSetChanged();
        shownAgent = na;
        fillAgent();
        agentSpinner.setSelection(agents.size() - 1);
    }

    private void onDeleteAgent() {
        if (Presets.isBaseAgent(shownAgent.id)) {
            Toast.makeText(this, "Questo è un agente di base: non si elimina, ma lo puoi modificare.", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Elimina agente")
                .setMessage("Elimino \"" + agentLabel(shownAgent) + "\"?")
                .setPositiveButton("Elimina", (d, w) -> {
                    int i = agents.indexOf(shownAgent);
                    if (i < 0) return;
                    agents.remove(i);
                    agentNames.remove(i);
                    int ni = Math.min(i, agents.size() - 1);
                    shownAgent = agents.get(ni);
                    agentAd.notifyDataSetChanged();
                    fillAgent();
                    agentSpinner.setSelection(ni);
                })
                .setNegativeButton("Annulla", null)
                .show();
    }

    private void onResetBase() {
        new AlertDialog.Builder(this)
                .setTitle("Ripristina agenti di base")
                .setMessage("Riporto Salvatore, Ingegnere e Revisore alle istruzioni originali. Gli altri agenti restano come sono.")
                .setPositiveButton("Ripristina", (d, w) -> {
                    commitAgent();
                    for (Model.Agent base : Presets.defaultAgents()) {
                        Model.Agent found = null;
                        for (Model.Agent a : agents) if (a.id.equals(base.id)) found = a;
                        if (found != null) {
                            found.name = base.name;
                            found.prompt = base.prompt;
                        } else {
                            agents.add(base);
                            agentNames.add(agentLabel(base));
                        }
                    }
                    agentNames.clear();
                    for (Model.Agent a : agents) agentNames.add(agentLabel(a));
                    agentAd.notifyDataSetChanged();
                    fillAgent();
                    agentSpinner.setSelection(Math.max(0, agents.indexOf(shownAgent)));
                })
                .setNegativeButton("Annulla", null)
                .show();
    }
}
