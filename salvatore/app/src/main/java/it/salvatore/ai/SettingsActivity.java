package it.salvatore.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
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
        labeled(bc, "Nome (lo scegli tu)", bName);
        labeled(bc, "Indirizzo", bUrl);
        labeled(bc, "Chiave di accesso", bKey);
        CheckBox show = new CheckBox(this);
        show.setText("Mostra la chiave");
        show.setTextColor(th.sub);
        show.setOnCheckedChangeListener((b, on) -> {
            int t = on ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_TEXT_VARIATION_PASSWORD;
            bKey.setInputType(InputType.TYPE_CLASS_TEXT | t);
        });
        bc.addView(show);
        labeled(bc, "Modello", bModel);

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

        TextView about = th.label("Salvatore · versione base 1.0\nLe chat, i cervelli e le chiavi restano solo su questo tablet.",
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
        bName.setText(shownBrain.name);
        bUrl.setText(shownBrain.url);
        bKey.setText(shownBrain.key);
        bModel.setText(shownBrain.model);
    }

    private void commitBrain() {
        if (shownBrain == null) return;
        shownBrain.name = bName.getText().toString().trim();
        shownBrain.url = bUrl.getText().toString().trim();
        shownBrain.key = bKey.getText().toString().trim();
        shownBrain.model = bModel.getText().toString().trim();
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
