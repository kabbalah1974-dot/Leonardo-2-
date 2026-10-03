package it.salvatore.ai;

import java.util.List;

/** Sceglie da solo il cervello: online quando c'è internet, dentro il tablet quando manca. */
public final class AutoBrain {

    private AutoBrain() {}

    public interface Ready {
        /** Il cervello locale ha il suo modello sul tablet? */
        boolean isReady(Model.Brain local);
    }

    public static final class Choice {
        public final Model.Brain primary;
        /** Se il primo non risponde, si prova questo (può essere null). */
        public final Model.Brain fallback;

        Choice(Model.Brain primary, Model.Brain fallback) {
            this.primary = primary;
            this.fallback = fallback;
        }
    }

    /** Un cervello "online": ha un indirizzo e non punta al tablet stesso. */
    static boolean isInternetBrain(Model.Brain b) {
        if (b == null || b.isLocal()) return false;
        String u = b.url == null ? "" : b.url.trim().toLowerCase(java.util.Locale.ROOT);
        if (u.isEmpty()) return false;
        return !(u.contains("127.0.0.1") || u.contains("localhost") || u.contains("0.0.0.0"));
    }

    public static Choice choose(List<Model.Brain> brains, Model.Brain active, boolean auto, boolean online, Ready ready) {
        if (!auto) return new Choice(active, null);

        Model.Brain local = null;
        if (active.isLocal() && ready.isReady(active)) {
            local = active;
        } else {
            for (Model.Brain b : brains) {
                if (b.isLocal() && ready.isReady(b)) {
                    local = b;
                    break;
                }
            }
        }

        Model.Brain remote = null;
        if (isInternetBrain(active)) {
            remote = active;
        } else {
            // prima uno con la chiave inserita, poi uno qualsiasi
            for (Model.Brain b : brains) {
                if (isInternetBrain(b) && b.key != null && !b.key.trim().isEmpty()) {
                    remote = b;
                    break;
                }
            }
            if (remote == null) {
                for (Model.Brain b : brains) {
                    if (isInternetBrain(b)) {
                        remote = b;
                        break;
                    }
                }
            }
        }

        if (online) {
            Model.Brain primary = remote != null ? remote : active;
            Model.Brain fallback = (!primary.isLocal() && local != null) ? local : null;
            return new Choice(primary, fallback);
        }
        return new Choice(local != null ? local : active, null);
    }
}
