package dev.isira.jaspring.signals;

import java.util.function.Supplier;

import dev.isira.jaspring.context.CurrentContext;

/// Who may write a signal. A signal captures the current owner when it is created (a live page binds
/// itself during its turns) and refuses writes from threads that do not hold that owner's turn.
/// Signals created with no owner, such as in singleton services, are unguarded shared state.
public interface SignalOwner {

    CurrentContext<SignalOwner> CURRENT = CurrentContext.threadLocal();

    boolean mayWriteFromCurrentThread();

    String describe();

    static SignalOwner current() {
        return CURRENT.get();
    }

    static <T> T runWith(SignalOwner owner, Supplier<T> body) {
        return CURRENT.runWith(owner, body);
    }
}
