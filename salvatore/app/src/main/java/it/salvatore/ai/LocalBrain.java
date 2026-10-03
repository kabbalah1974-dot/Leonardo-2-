package it.salvatore.ai;

import android.content.Context;
import com.google.ai.edge.litertlm.Backend;
import com.google.ai.edge.litertlm.Content;
import com.google.ai.edge.litertlm.Contents;
import com.google.ai.edge.litertlm.Conversation;
import com.google.ai.edge.litertlm.ConversationConfig;
import com.google.ai.edge.litertlm.Engine;
import com.google.ai.edge.litertlm.EngineConfig;
import com.google.ai.edge.litertlm.Message;
import com.google.ai.edge.litertlm.MessageCallback;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Il cervello dentro il tablet: fa girare un modello piccolo con il motore di Google, senza internet. */
public final class LocalBrain implements OpenAiClient.LocalEngine {

    private static final int MAX_TOKENS = 2048;
    private static final int MAX_HISTORY = 6;
    private static final int MAX_CHARS = 5000;

    private static LocalBrain instance;

    /** Da chiamare all'avvio delle schermate: collega il cervello locale al resto dell'app. */
    public static synchronized void install(Context c) {
        if (instance == null) {
            instance = new LocalBrain(c.getApplicationContext());
        }
        OpenAiClient.local = instance;
    }

    /** Libera la memoria occupata dal modello (si ricarica da solo alla prossima risposta). */
    public static void release() {
        LocalBrain i = instance;
        if (i != null) i.closeEngine(false);
    }

    private final Context ctx;
    private final Object lock = new Object();
    private Engine engine;
    private String enginePath = "";
    private long engineStamp = 0;
    private volatile boolean generating = false;

    private LocalBrain(Context c) {
        ctx = c;
    }

    private void closeEngine(boolean force) {
        if (generating && !force) return; // non si tocca il modello mentre sta rispondendo
        synchronized (lock) {
            if (engine != null) {
                try {
                    engine.close();
                } catch (Throwable ignored) {
                }
                engine = null;
                enginePath = "";
            }
        }
    }

    @Override
    public String chat(Model.Brain brain, List<Model.Msg> messages, OpenAiClient.Sink sink, OpenAiClient.Cancel cancel)
            throws OpenAiClient.ChatException {
        if (!ModelStore.isReady(ctx, brain.model)) {
            throw new OpenAiClient.ChatException(
                    "Il modello non è ancora sul tablet. Apri Impostazioni, scegli questo cervello e tocca \"Scarica il modello\".");
        }
        File f = ModelStore.file(ctx, brain.model);

        // Istruzioni, storia e ultima domanda
        String system = "";
        List<Model.Msg> rest = new ArrayList<>();
        for (Model.Msg m : messages) {
            if ("system".equals(m.role)) system = m.content;
            else rest.add(m);
        }
        int lastUser = -1;
        for (int i = rest.size() - 1; i >= 0; i--) {
            if ("user".equals(rest.get(i).role)) {
                lastUser = i;
                break;
            }
        }
        if (lastUser < 0) throw new OpenAiClient.ChatException("Non c'è nessuna domanda da fare al cervello.");
        String question = rest.get(lastUser).content;
        List<Model.Msg> hist = new ArrayList<>(rest.subList(0, lastUser));
        while (hist.size() > MAX_HISTORY) hist.remove(0);
        while (!hist.isEmpty() && !"user".equals(hist.get(0).role)) hist.remove(0);
        int chars = system.length() + question.length();
        for (Model.Msg m : hist) chars += m.content.length();
        while (!hist.isEmpty() && chars > MAX_CHARS) {
            chars -= hist.remove(0).content.length();
            while (!hist.isEmpty() && !"user".equals(hist.get(0).role)) chars -= hist.remove(0).content.length();
        }
        if (system.trim().isEmpty()) system = "Sei un assistente. Rispondi in italiano.";

        synchronized (lock) {
            generating = true;
            Conversation conv = null;
            try {
                Engine e = ensureEngine(f);
                List<Message> initial = new ArrayList<>();
                for (Model.Msg m : hist) {
                    initial.add("user".equals(m.role) ? Message.Companion.user(m.content) : Message.Companion.model(m.content));
                }
                ConversationConfig cc = new ConversationConfig(Contents.Companion.of(system), initial);
                conv = e.createConversation(cc);
                final Conversation fc = conv;
                cancel.setOnCancel(() -> {
                    try {
                        fc.cancelProcess();
                    } catch (Throwable ignored) {
                    }
                });

                final StringBuilder out = new StringBuilder();
                final ThinkFilter think = new ThinkFilter();
                final StringBuilder raw = new StringBuilder();
                final CountDownLatch done = new CountDownLatch(1);
                final Throwable[] err = new Throwable[1];
                final OpenAiClient.Sink fs = sink;

                fc.sendMessageAsync(question, new MessageCallback() {
                    @Override
                    public void onMessage(Message m) {
                        String t = textOf(m);
                        if (t.isEmpty()) return;
                        String piece = t;
                        // Se il motore manda il testo intero ogni volta invece dei pezzi nuovi, si prende solo il nuovo.
                        if (raw.length() > 0 && t.length() > raw.length() && t.startsWith(raw.toString())) {
                            piece = t.substring(raw.length());
                            raw.setLength(0);
                            raw.append(t);
                        } else {
                            raw.append(t);
                        }
                        String vis = think.feed(piece);
                        if (!vis.isEmpty()) {
                            out.append(vis);
                            if (fs != null) fs.onDelta(vis);
                        }
                    }

                    @Override
                    public void onDone() {
                        done.countDown();
                    }

                    @Override
                    public void onError(Throwable t) {
                        err[0] = t;
                        done.countDown();
                    }
                });

                boolean finished;
                try {
                    finished = done.await(15, TimeUnit.MINUTES);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    finished = false;
                }
                if (!finished) {
                    try {
                        fc.cancelProcess();
                    } catch (Throwable ignored) {
                    }
                    throw new OpenAiClient.ChatException("Il modello ci mette troppo a rispondere. Riprova con una domanda più corta.");
                }
                String tail = think.finish();
                if (!tail.isEmpty()) {
                    out.append(tail);
                    if (fs != null) fs.onDelta(tail);
                }
                if (err[0] != null && !cancel.cancelled) {
                    throw new OpenAiClient.ChatException("Il modello ha avuto un problema: " + err[0]);
                }
                if (out.length() == 0 && !cancel.cancelled) {
                    throw new OpenAiClient.ChatException("Il modello non ha dato nessuna risposta. Riprova.");
                }
                return out.toString();
            } catch (OpenAiClient.ChatException ce) {
                throw ce;
            } catch (OutOfMemoryError oom) {
                closeEngine(true);
                throw new OpenAiClient.ChatException("Memoria del tablet esaurita. Chiudi le altre app e riprova.");
            } catch (Throwable t) {
                throw new OpenAiClient.ChatException("Il cervello dentro il tablet non parte: " + t);
            } finally {
                cancel.setOnCancel(null);
                if (conv != null) {
                    try {
                        conv.close();
                    } catch (Throwable ignored) {
                    }
                }
                generating = false;
            }
        }
    }

    private Engine ensureEngine(File f) throws OpenAiClient.ChatException {
        String path = f.getAbsolutePath();
        long stamp = f.lastModified();
        if (engine != null && path.equals(enginePath) && stamp == engineStamp) return engine;
        closeEngine(true);
        try {
            EngineConfig cfg = new EngineConfig(path, new Backend.CPU(), null, null, Integer.valueOf(MAX_TOKENS), null,
                    ctx.getCacheDir().getAbsolutePath());
            Engine e = new Engine(cfg);
            e.initialize(); // la prima volta può volerci un po'
            engine = e;
            enginePath = path;
            engineStamp = stamp;
            return e;
        } catch (OutOfMemoryError oom) {
            throw new OpenAiClient.ChatException("Memoria del tablet esaurita. Chiudi le altre app e riprova.");
        } catch (Throwable t) {
            throw new OpenAiClient.ChatException("Non riesco ad avviare il modello (il file potrebbe essere incompleto o non adatto): " + t);
        }
    }

    private static String textOf(Message m) {
        StringBuilder b = new StringBuilder();
        try {
            for (Content c : m.getContents().getContents()) {
                if (c instanceof Content.Text) b.append(((Content.Text) c).getText());
            }
        } catch (Throwable ignored) {
        }
        return b.toString();
    }
}
