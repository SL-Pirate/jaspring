package dev.isira.jaspring.context;

import java.util.function.Supplier;

final class ThreadLocalContext<T> implements CurrentContext<T> {

    private final ThreadLocal<T> holder = new ThreadLocal<>();

    @Override
    public T get() {
        return holder.get();
    }

    @Override
    public <R> R runWith(T value, Supplier<R> body) {
        final var previous = holder.get();
        bind(value);
        try {
            return body.get();
        } finally {
            bind(previous);
        }
    }

    private void bind(T value) {
        if (value == null) {
            holder.remove();
        } else {
            holder.set(value);
        }
    }
}
