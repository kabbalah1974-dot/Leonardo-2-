package it.leonardo.antivirus;

import java.util.Objects;

/**
 * Tiene la distanza minima tra due richieste a VirusTotal, qualunque controllo le faccia
 * (app o file): il piano gratuito ne ammette 4 al minuto.
 */
public final class RateGate {

    public interface Clock {
        long nowMs();
    }

    public interface Sleeper {
        void sleep(long ms) throws InterruptedException;
    }

    private final long gapMs;
    private final Clock clock;
    private final Sleeper sleeper;
    private long lastMs;
    private boolean used;

    public RateGate(long gapMs, Clock clock, Sleeper sleeper) {
        if (gapMs < 0) throw new IllegalArgumentException("gapMs < 0");
        this.gapMs = gapMs;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    }

    /** Aspetta, se serve, che sia passato abbastanza tempo dall'ultima richiesta; poi la registra. */
    public synchronized void awaitTurn() throws InterruptedException {
        if (used) {
            long wait = gapMs - (clock.nowMs() - lastMs);
            if (wait > 0) sleeper.sleep(wait);
        }
        lastMs = clock.nowMs();
        used = true;
    }

    /** Registra una pausa imposta da VirusTotal (429): la prossima richiesta riparte da ora. */
    public synchronized void noteBackoff() {
        lastMs = clock.nowMs();
        used = true;
    }
}
