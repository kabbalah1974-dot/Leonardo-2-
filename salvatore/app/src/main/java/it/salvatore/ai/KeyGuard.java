package it.salvatore.ai;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Protegge le chiavi: una chiave già salvata non diventa vuota per sbaglio. Si toglie solo con l'apposito tasto. */
public final class KeyGuard {

    private KeyGuard() {}

    /** Rimette le chiavi che stavano per essere cancellate. Restituisce i nomi dei cervelli salvati in extremis. */
    public static java.util.List<String> protect(List<Model.Brain> now, List<Model.Brain> stored, Set<String> cleared) {
        Map<String, String> old = new HashMap<>();
        for (Model.Brain b : stored) {
            if (b.key != null && !b.key.trim().isEmpty()) old.put(b.id, b.key);
        }
        java.util.List<String> saved = new java.util.ArrayList<>();
        for (Model.Brain b : now) {
            boolean empty = b.key == null || b.key.trim().isEmpty();
            String was = old.get(b.id);
            if (empty && was != null && (cleared == null || !cleared.contains(b.id))) {
                b.key = was;
                saved.add(b.name);
            }
        }
        return saved;
    }
}
