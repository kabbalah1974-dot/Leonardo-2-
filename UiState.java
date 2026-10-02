package it.leonardo.antivirus;

import java.util.Collections;
import java.util.List;

/** Fotografia di ciò che l'interfaccia deve mostrare. Viene sostituita, mai modificata a metà. */
public final class UiState {
    public boolean hasKey;
    public boolean busy;
    /** Vero appena finita l'analisi locale delle app: da qui il punteggio è già valido. */
    public boolean appsDone;
    public String status = "";
    public int appsChecked;
    public List<Finding> appFindings = Collections.emptyList();
    public List<Finding> fileFindings = Collections.emptyList();
    public List<HistoryEntry> history = Collections.emptyList();

    public UiState() {
    }

    public UiState(UiState other) {
        hasKey = other.hasKey;
        busy = other.busy;
        appsDone = other.appsDone;
        status = other.status;
        appsChecked = other.appsChecked;
        appFindings = other.appFindings;
        fileFindings = other.fileFindings;
        history = other.history;
    }
}
