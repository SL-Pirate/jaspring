package dev.isira.jaspring.boot;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.context.SmartLifecycle;

import dev.isira.jaspring.live.LivePageRegistry;

/// Evicts expired pages every ten seconds on a virtual thread; stops with the context.
public final class LivePageSweeper implements SmartLifecycle {

    private final LivePageRegistry registry;
    private ScheduledExecutorService executor;
    private volatile boolean running;

    private LivePageSweeper(LivePageRegistry registry) {
        this.registry = registry;
    }

    static LivePageSweeper every10Seconds(LivePageRegistry registry) {
        return new LivePageSweeper(registry);
    }

    static LivePageSweeper disabled() {
        return new LivePageSweeper(null);
    }

    @Override
    public void start() {
        if (registry == null) return;
        executor = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("jaspring-sweeper").factory());
        executor.scheduleAtFixedRate(registry::evictExpired, 10, 10, TimeUnit.SECONDS);
        running = true;
    }

    @Override
    public void stop() {
        if (executor != null) executor.shutdownNow();
        if (registry != null) registry.destroyAll();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
