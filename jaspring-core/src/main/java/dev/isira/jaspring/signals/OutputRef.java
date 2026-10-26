package dev.isira.jaspring.signals;

import java.util.function.Consumer;

public interface OutputRef<T> {

    Subscription subscribe(Consumer<T> listener);
}
