package it.leonardo.antivirus;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Schermata unica: punteggio di sicurezza, due azioni, segnalazioni a schede e cronologia. */
public class MainActivity extends Activity implements ScanEngine.Listener {

    private static final int REQ_PICK_FILES = 42;
    private static final int MAX_WIDTH_DP = 720;

    private static final int ACCENT = 0xFF2F7DE1;
    private static final int C_HIGH = 0xFFD93025;
    private static final int C_MEDIUM = 0xFFE8710A;
    private static final int C_LOW = 0xFFC9A100;
    private static final int C_UNKNOWN = 0xFF7B8794;
    private static final int C_OK = 0xFF1E9E5A;

    private boolean night;
    private int colBg;
    private int colCard;
    private int colText;
    private int colSoft;
    private int colLine;

    private ScanEngine engine;
    private ScrollView scroll;
    private LinearLayout column;
    private ShieldView shield;
    private TextView headline;
    private TextView subline;
    private TextView chipHigh;
    private TextView chipMedium;
    private TextView chipLow;
    private LinearLayout chipRow;
    private TextView buttonApps;
    private TextView buttonFiles;
    private TextView buttonStop;
    private TextView keyTip;
    private ProgressBar progress;
    private TextView statusText;
    private LinearLayout results;
    private LinearLayout historyBox;
    private List<Finding> shownApps;
    private List<Finding> shownFiles;
    private List<HistoryEntry> shownHistory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        colBg = night ? 0xFF0E1318 : 0xFFF2F5F9;
        colCard = night ? 0xFF19212A : 0xFFFFFFFF;
        colText = night ? 0xFFE9EEF3 : 0xFF17202B;
        colSoft = night ? 0xFF9BA7B3 : 0xFF5B6773;
        colLine = night ? 0xFF2A3541 : 0xFFE0E6ED;

        getWindow().setStatusBarColor(colBg);
        getWindow().setNavigationBarColor(colBg);
        if (!night) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }

        if (getActionBar() != null) getActionBar().hide();
        engine = ScanEngine.get(this);
        setContentView(buildUi());
        applyColumnWidth();
    }

    @Override
    protected void onStart() {
        super.onStart();
        engine.setListener(this);
    }

    @Override
    protected void onStop() {
        engine.removeListener(this);
        super.onStop();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyColumnWidth();
    }

    // ---------- costruzione della schermata ----------

    private View buildUi() {
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(colBg);

        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(16), dp(20), dp(16), dp(32));
        scroll.addView(column, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        // intestazione
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text("Leonardo", 28, true, colText));
        titles.addView(text("Antivirus per il tuo tablet", 14, false, colSoft));
        header.addView(titles, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView settings = pill("Impostazioni", colSoft);
        settings.setMinHeight(dp(48));
        settings.setGravity(Gravity.CENTER);
        settings.setOnClickListener(v -> showKeyDialog());
        header.addView(settings);
        column.addView(header);

        // scheda principale con il punteggio
        LinearLayout hero = card();
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(dp(20), dp(24), dp(20), dp(20));
        shield = new ShieldView(this);
        hero.addView(shield, new LinearLayout.LayoutParams(dp(170), dp(170)));
        headline = text("Non ancora controllato", 22, true, colText);
        headline.setGravity(Gravity.CENTER);
        hero.addView(headline, topMargin(14));
        subline = text("Premi “Controlla le app” per ottenere il punteggio.", 14, false, colSoft);
        subline.setGravity(Gravity.CENTER);
        hero.addView(subline, topMargin(6));

        chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setGravity(Gravity.CENTER);
        chipHigh = chip(C_HIGH);
        chipMedium = chip(C_MEDIUM);
        chipLow = chip(C_LOW);
        chipRow.addView(chipHigh);
        chipRow.addView(chipMedium, leftMargin(8));
        chipRow.addView(chipLow, leftMargin(8));
        chipRow.setVisibility(View.GONE);
        hero.addView(chipRow, topMargin(14));
        column.addView(hero, topMargin(16));

        // azioni
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        buttonApps = button("Controlla le app", true);
        buttonApps.setOnClickListener(v -> engine.scanApps());
        buttonFiles = button("Controlla file", false);
        buttonFiles.setOnClickListener(v -> pickFiles());
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        l2.leftMargin = dp(10);
        actions.addView(buttonApps, l1);
        actions.addView(buttonFiles, l2);
        column.addView(actions, topMargin(14));

        keyTip = text("Aggiungi la chiave VirusTotal per il controllo online  ›", 13, true, ACCENT);
        keyTip.setGravity(Gravity.CENTER);
        keyTip.setPadding(0, dp(10), 0, dp(4));
        keyTip.setOnClickListener(v -> showKeyDialog());
        keyTip.setVisibility(View.GONE);
        column.addView(keyTip, topMargin(2));

        // stato del lavoro in corso
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setIndeterminateTintList(ColorStateList.valueOf(ACCENT));
        progress.setVisibility(View.GONE);
        column.addView(progress, topMargin(10));
        statusText = text("", 14, false, colSoft);
        statusText.setGravity(Gravity.CENTER);
        statusText.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        column.addView(statusText, topMargin(6));
        buttonStop = button("Interrompi", false);
        buttonStop.setVisibility(View.GONE);
        buttonStop.setOnClickListener(v -> engine.cancel());
        column.addView(buttonStop, topMargin(8));

        // risultati
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        column.addView(results, topMargin(4));

        // cronologia
        historyBox = new LinearLayout(this);
        historyBox.setOrientation(LinearLayout.VERTICAL);
        column.addView(historyBox, topMargin(8));

        // informazioni oneste
        LinearLayout info = card();
        info.addView(text("Cosa può e non può fare", 16, true, colText));
        info.addView(text("Android non permette a un'app di guardare dentro le altre. Questo controllo guarda da dove "
                + "arrivano le app, che permessi hanno e, con la chiave VirusTotal, se i loro file sono già noti come pericolosi.",
                13, false, colSoft), topMargin(6));
        info.addView(text("Un risultato pulito riduce il dubbio ma non è una garanzia: un virus nuovo, mai analizzato, "
                + "non viene riconosciuto. Online va solo l'impronta del file, mai il file.", 13, false, colSoft), topMargin(6));
        info.addView(text("Il controllo online richiede qualche minuto: tieni l'app aperta finché non finisce.",
                13, false, colSoft), topMargin(6));
        column.addView(info, topMargin(20));

        return scroll;
    }

    private void applyColumnWidth() {
        int screen = getResources().getDisplayMetrics().widthPixels;
        int width = Math.min(screen, dp(MAX_WIDTH_DP));
        column.setLayoutParams(new FrameLayout.LayoutParams(
                width, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
    }

    // ---------- aggiornamento dallo stato ----------

    @Override
    public void onState(UiState s) {
        statusText.setText(s.status);
        statusText.setVisibility(s.status.isEmpty() ? View.GONE : View.VISIBLE);
        buttonApps.setEnabled(!s.busy);
        buttonFiles.setEnabled(!s.busy);
        buttonApps.setAlpha(s.busy ? 0.5f : 1f);
        buttonFiles.setAlpha(s.busy ? 0.5f : 1f);
        buttonStop.setVisibility(s.busy ? View.VISIBLE : View.GONE);
        progress.setVisibility(s.busy ? View.VISIBLE : View.GONE);
        keyTip.setVisibility(s.hasKey ? View.GONE : View.VISIBLE);

        updateHero(s);

        if (s.history != shownHistory) {
            shownHistory = s.history;
            renderHistory(s.history);
        }

        if (s.appFindings != shownApps || s.fileFindings != shownFiles) {
            shownApps = s.appFindings;
            shownFiles = s.fileFindings;
            renderResults(s);
        }
    }

    private void updateHero(UiState s) {
        if (!s.appsDone) {
            shield.setScore(-1);
            shield.setContentDescription("Punteggio di sicurezza non ancora calcolato");
            chipRow.setVisibility(View.GONE);
            if (s.busy) {
                headline.setText("Controllo in corso");
                subline.setText("Sto guardando le app installate.");
            } else {
                headline.setText("Non ancora controllato");
                subline.setText("Premi “Controlla le app” per ottenere il punteggio.");
            }
            return;
        }
        int[] c = Reports.countBySeverity(s.appFindings);
        int score = RiskPolicy.deviceScore(c[0], c[1], c[2]);
        shield.setScore(score);
        if (score >= 85) headline.setText("Tutto sotto controllo");
        else if (score >= 60) headline.setText("Qualcosa da guardare");
        else headline.setText("Serve attenzione");
        subline.setText("Controllate " + s.appsChecked + " app"
                + (s.busy ? ": sto ancora facendo il controllo online." : ".")
                + " Il punteggio è una stima su permessi e origine delle app, non una garanzia.");
        shield.setContentDescription("Punteggio di sicurezza " + score + " su 100");
        chipHigh.setText("Alto " + c[0]);
        chipMedium.setText("Medio " + c[1]);
        chipLow.setText("Basso " + c[2]);
        chipRow.setVisibility(View.VISIBLE);
    }

    private void renderResults(UiState s) {
        results.removeAllViews();
        if (!s.appFindings.isEmpty()) {
            results.addView(sectionTitle("App da guardare", s.appFindings.size()));
            for (Finding f : s.appFindings) results.addView(findingCard(f), topMargin(8));
        } else if (s.appsDone) {
            LinearLayout ok = card();
            ok.addView(text("Nessuna app da segnalare", 16, true, C_OK));
            ok.addView(text("Non ho trovato app con permessi o origini sospette.", 13, false, colSoft), topMargin(4));
            results.addView(ok, topMargin(14));
        }
        if (!s.fileFindings.isEmpty()) {
            results.addView(sectionTitle("File controllati", s.fileFindings.size()));
            for (Finding f : s.fileFindings) results.addView(findingCard(f), topMargin(8));
        }
    }

    private View sectionTitle(String title, int count) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(18), dp(4), 0);
        row.addView(text(title, 18, true, colText), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(text(String.valueOf(count), 14, true, colSoft));
        return row;
    }

    private View findingCard(Finding f) {
        final int color = colorOf(f.severity);

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.HORIZONTAL);
        outer.setBackground(rounded(colCard, 16, colLine));

        View bar = new View(this);
        bar.setBackground(rounded(color, 3, 0));
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(dp(5), LinearLayout.LayoutParams.MATCH_PARENT);
        barLp.setMargins(dp(8), dp(12), 0, dp(12));
        outer.addView(bar, barLp);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), dp(14), dp(14), dp(14));
        outer.addView(body, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(f.title, 16, true, colText);
        top.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView badge = text(f.severity.label, 12, true, 0xFFFFFFFF);
        badge.setPadding(dp(10), dp(3), dp(10), dp(3));
        badge.setBackground(rounded(color, 20, 0));
        top.addView(badge);
        body.addView(top);

        if (!f.subtitle.isEmpty()) body.addView(text(f.subtitle, 12, false, colSoft), topMargin(3));

        final int shown = 2;
        final List<TextView> extra = new ArrayList<>();
        for (int i = 0; i < f.reasons.size(); i++) {
            TextView r = text("•  " + f.reasons.get(i), 14, false, colText);
            if (i >= shown) {
                r.setVisibility(View.GONE);
                extra.add(r);
            }
            body.addView(r, topMargin(6));
        }
        if (!extra.isEmpty()) {
            final TextView more = text("Mostra altri " + extra.size() + "  ›", 13, true, ACCENT);
            more.setPadding(0, dp(8), 0, 0);
            more.setOnClickListener(v -> {
                for (TextView t : extra) t.setVisibility(View.VISIBLE);
                more.setVisibility(View.GONE);
            });
            body.addView(more);
        }
        return outer;
    }

    private int colorOf(Severity severity) {
        switch (severity) {
            case HIGH: return C_HIGH;
            case MEDIUM: return C_MEDIUM;
            case LOW: return C_LOW;
            case UNKNOWN: return C_UNKNOWN;
            default: return C_OK;
        }
    }

    // ---------- cronologia ----------

    private void renderHistory(List<HistoryEntry> entries) {
        historyBox.removeAllViews();
        if (entries.isEmpty()) return;

        LinearLayout box = card();
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("Controlli precedenti", 16, true, colText),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView clear = text("Cancella", 13, true, ACCENT);
        clear.setGravity(Gravity.CENTER);
        clear.setMinHeight(dp(48));
        clear.setPadding(dp(12), 0, 0, 0);
        clear.setOnClickListener(v -> engine.clearHistory());
        head.addView(clear);
        box.addView(head);

        SimpleDateFormat fmt = new SimpleDateFormat("d MMM, HH:mm", Locale.ITALY);
        for (HistoryEntry e : entries) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            View dot = new View(this);
            dot.setBackground(rounded(scoreColor(e.score), 10, 0));
            row.addView(dot, new LinearLayout.LayoutParams(dp(10), dp(10)));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(dp(12), 0, 0, 0);
            col.addView(text(fmt.format(new Date(e.whenMs)), 14, true, colText));
            col.addView(text(e.apps + " app · alto " + e.high + " · medio " + e.medium + " · basso " + e.low,
                    12, false, colSoft));
            row.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(text(String.valueOf(e.score), 20, true, scoreColor(e.score)));
            box.addView(row, topMargin(12));
        }
        historyBox.addView(box, topMargin(12));
    }

    private static int scoreColor(int score) {
        if (score >= 85) return C_OK;
        if (score >= 60) return C_MEDIUM;
        return C_HIGH;
    }

    // ---------- chiave VirusTotal e scelta dei file ----------

    private void showKeyDialog() {
        final EditText input = new EditText(this);
        input.setHint("API key");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        FrameLayout holder = new FrameLayout(this);
        holder.setPadding(dp(20), dp(8), dp(20), 0);
        holder.addView(input);

        String message = "Serve per il controllo online. Crea un account gratuito su virustotal.com, "
                + "apri il tuo profilo e copia la API key qui sotto. Resta cifrata sul dispositivo.";
        if (engine.hasKey()) message += "\n\nUna chiave è già salvata. Incollane una nuova per sostituirla.";

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Chiave VirusTotal")
                .setMessage(message)
                .setView(holder)
                .setPositiveButton("Salva", (d, w) -> {
                    String key = input.getText().toString().trim();
                    if (!key.isEmpty()) engine.saveKey(key);
                })
                .setNegativeButton("Annulla", null);
        if (engine.hasKey()) builder.setNeutralButton("Rimuovi", (d, w) -> engine.saveKey(""));
        builder.show();
    }

    private void pickFiles() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, REQ_PICK_FILES);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_FILES || resultCode != RESULT_OK || data == null) return;
        List<Uri> uris = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) uris.add(clip.getItemAt(i).getUri());
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        engine.scanFiles(uris);
    }

    // ---------- piccoli aiuti per costruire le viste ----------

    private TextView text(String value, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        c.setBackground(rounded(colCard, 16, colLine));
        return c;
    }

    private TextView button(String label, boolean primary) {
        TextView b = text(label, 16, true, primary ? 0xFFFFFFFF : ACCENT);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), dp(14), dp(12), dp(14));
        GradientDrawable base = primary ? rounded(ACCENT, 14, 0) : rounded(colCard, 14, ACCENT);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33808080), base, null));
        b.setClickable(true);
        return b;
    }

    private TextView pill(String label, int color) {
        TextView t = text(label, 13, true, color);
        t.setPadding(dp(12), dp(7), dp(12), dp(7));
        t.setBackground(rounded(colCard, 20, colLine));
        t.setClickable(true);
        return t;
    }

    private TextView chip(int color) {
        TextView t = text("", 13, true, color);
        t.setPadding(dp(12), dp(5), dp(12), dp(5));
        t.setBackground(rounded(night ? 0xFF222C37 : 0xFFF2F5F9, 20, color));
        return t;
    }

    private GradientDrawable rounded(int fill, int radiusDp, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(radiusDp));
        d.setColor(fill);
        if (strokeColor != 0) d.setStroke(dp(1), strokeColor);
        return d;
    }

    private LinearLayout.LayoutParams topMargin(int dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(dp);
        return lp;
    }

    private LinearLayout.LayoutParams leftMargin(int dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(dp);
        return lp;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }

    // ---------- anello del punteggio ----------

    /** Anello colorato con il punteggio al centro. Punteggio -1 = non ancora calcolato. */
    private final class ShieldView extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint number = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private float shown;
        private int target = -1;
        private ValueAnimator animator;

        ShieldView(Context context) {
            super(context);
            float stroke = dp(14);
            track.setStyle(Paint.Style.STROKE);
            track.setStrokeWidth(stroke);
            track.setColor(colLine);
            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeWidth(stroke);
            arc.setStrokeCap(Paint.Cap.ROUND);
            number.setTextAlign(Paint.Align.CENTER);
            number.setTypeface(Typeface.DEFAULT_BOLD);
            number.setTextSize(dp(46));
            small.setTextAlign(Paint.Align.CENTER);
            small.setTextSize(dp(13));
            small.setColor(colSoft);
        }

        void setScore(int score) {
            if (score == target) return;
            target = score;
            if (animator != null) animator.cancel();
            float to = score < 0 ? 0f : score;
            animator = ValueAnimator.ofFloat(shown, to);
            animator.setDuration(score < 0 ? 200 : 800);
            animator.addUpdateListener(a -> {
                shown = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float pad = dp(14);
            box.set(pad, pad, getWidth() - pad, getHeight() - pad);
            canvas.drawArc(box, 0, 360, false, track);
            int color = target < 0 ? colLine : scoreColor(target);
            if (shown > 0) {
                arc.setColor(color);
                canvas.drawArc(box, -90, 360f * shown / 100f, false, arc);
            }
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            if (target < 0) {
                number.setColor(colSoft);
                canvas.drawText("–", cx, cy + dp(14), number);
            } else {
                number.setColor(color);
                canvas.drawText(String.valueOf(Math.round(shown)), cx, cy + dp(10), number);
                canvas.drawText("su 100", cx, cy + dp(32), small);
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (animator != null) animator.cancel();
        }
    }
}
