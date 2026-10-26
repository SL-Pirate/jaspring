package dev.isira.jaspring.di;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;

import dev.isira.jaspring.context.CurrentContext;

/// The `jaspring-page` Spring scope: one bean instance per live page, which is how Angular's component-provided
/// services map onto Spring. The page binds its `Beans` holder for the duration of each turn.
public final class PageScope implements Scope {

    public static final String NAME = "jaspring-page";

    private static final CurrentContext<Beans> CURRENT = CurrentContext.threadLocal();

    public static <T> T runWith(Beans beans, java.util.function.Supplier<T> body) {
        return CURRENT.runWith(beans, body);
    }

    @Override
    public Object get(String name, ObjectFactory<?> objectFactory) {
        return current().get(name, objectFactory);
    }

    @Override
    public Object remove(String name) {
        return current().remove(name);
    }

    @Override
    public void registerDestructionCallback(String name, Runnable callback) {
        current().destructionCallbacks.put(name, callback);
    }

    @Override
    public Object resolveContextualObject(String key) {
        return null;
    }

    @Override
    public String getConversationId() {
        return current().id;
    }

    private static Beans current() {
        final var beans = CURRENT.get();
        if (beans == null) {
            throw new IllegalStateException("No live page is active on this thread; jaspring-page scoped beans need a page turn");
        }
        return beans;
    }

    /// The page-scoped bean instances of one live page. Destroyed with the page.
    public static final class Beans {
        private final String id;
        private final Map<String, Object> instances = new LinkedHashMap<>();
        private final Map<String, Runnable> destructionCallbacks = new LinkedHashMap<>();

        public Beans(String id) {
            this.id = id;
        }

        synchronized Object get(String name, ObjectFactory<?> factory) {
            return instances.computeIfAbsent(name, n -> factory.getObject());
        }

        synchronized Object remove(String name) {
            destructionCallbacks.remove(name);
            return instances.remove(name);
        }

        public synchronized void destroy() {
            destructionCallbacks.values().forEach(Runnable::run);
            destructionCallbacks.clear();
            instances.clear();
        }
    }
}
