package dev.isira.jaspring.typescript.internal;

import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;

/// The bundle in a GraalJS context with no host or I/O access: the only thing that crosses the boundary is one
/// JSON string each way. The context is single-threaded, so calls are serialised with a lock.
public final class GraalBundleHost implements BundleHost {

    private final Engine engine;
    private final Context context;
    private final Value run;
    private final ReentrantLock lock = new ReentrantLock();

    private GraalBundleHost(Engine engine, Context context, Value run) {
        this.engine = engine;
        this.context = context;
        this.run = run;
    }

    public static GraalBundleHost load() {
        final var engine = Engine.newBuilder("js")
                .option("engine.WarnInterpreterOnly", "false")
                .build();
        final var context = Context.newBuilder("js")
                .engine(engine)
                .option("js.esm-eval-returns-exports", "true")
                .option("js.text-encoding", "true")
                .allowHostAccess(HostAccess.NONE)
                .allowIO(IOAccess.NONE)
                .build();
        try {
            final var exports = context.eval(Bundle.source());
            return new GraalBundleHost(engine, context, exports.getMember("run"));
        } catch (RuntimeException e) {
            context.close();
            engine.close();
            throw e;
        }
    }

    @Override
    public String run(String requestJson) {
        lock.lock();
        try {
            return run.execute(requestJson).asString();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean interpreted() {
        return "Interpreted".equals(engine.getImplementationName());
    }

    @Override
    public void close() {
        lock.lock();
        try {
            context.close();
            engine.close();
        } finally {
            lock.unlock();
        }
    }
}
