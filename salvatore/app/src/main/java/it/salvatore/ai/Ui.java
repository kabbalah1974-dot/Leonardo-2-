package it.salvatore.ai;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

/** Colori e piccoli attrezzi per disegnare le schermate (chiaro e scuro). */
final class Ui {

    final Context ctx;
    final boolean night;
    final float density;
    final int bg, card, text, sub, accent, onAccent, userBubble, userText, botBubble, input, danger;

    Ui(Context c) {
        ctx = c;
        density = c.getResources().getDisplayMetrics().density;
        night = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        if (night) {
            bg = Color.parseColor("#121413");
            card = Color.parseColor("#1C201E");
            text = Color.parseColor("#ECEFEC");
            sub = Color.parseColor("#9AA39E");
            accent = Color.parseColor("#6FB09B");
            onAccent = Color.parseColor("#0E1512");
            userBubble = Color.parseColor("#2F5D50");
            userText = Color.parseColor("#FFFFFF");
            botBubble = Color.parseColor("#1E2321");
            input = Color.parseColor("#1E2321");
            danger = Color.parseColor("#F2B8B5");
        } else {
            bg = Color.parseColor("#F4F1EA");
            card = Color.parseColor("#FFFFFF");
            text = Color.parseColor("#1F2421");
            sub = Color.parseColor("#6B726E");
            accent = Color.parseColor("#2F5D50");
            onAccent = Color.parseColor("#FFFFFF");
            userBubble = Color.parseColor("#2F5D50");
            userText = Color.parseColor("#FFFFFF");
            botBubble = Color.parseColor("#FFFFFF");
            input = Color.parseColor("#FFFFFF");
            danger = Color.parseColor("#B3261E");
        }
    }

    int dp(int v) {
        return Math.round(v * density);
    }

    GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    TextView label(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    Button button(String s, View.OnClickListener l) {
        Button b = new Button(ctx);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setOnClickListener(l);
        return b;
    }

    EditText field(String hint, boolean multiline) {
        EditText e = new EditText(ctx);
        e.setHint(hint);
        e.setHintTextColor(sub);
        e.setTextColor(text);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        e.setBackground(rounded(input, 12));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        if (multiline) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            e.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        } else {
            e.setInputType(InputType.TYPE_CLASS_TEXT);
            e.setSingleLine(true);
        }
        return e;
    }
}
