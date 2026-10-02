package it.leonardo.antivirus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Esito del giudizio su un'app: punteggio di rischio e motivi in italiano semplice. */
public final class Assessment {
    public final int score;
    public final List<String> reasons;

    Assessment(int score, List<String> reasons) {
        this.score = score;
        this.reasons = Collections.unmodifiableList(new ArrayList<>(reasons));
    }

    public Severity severity() {
        return RiskPolicy.severityForScore(score);
    }
}
