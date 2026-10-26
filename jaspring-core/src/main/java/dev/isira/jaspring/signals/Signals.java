package dev.isira.jaspring.signals;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import dev.isira.jaspring.signals.internal.ComputedNode;
import dev.isira.jaspring.signals.internal.EffectNode;
import dev.isira.jaspring.signals.internal.Graph;
import dev.isira.jaspring.signals.internal.InputSignalNode;
import dev.isira.jaspring.signals.internal.LinkedSignalNode;
import dev.isira.jaspring.signals.internal.ModelSignalNode;
import dev.isira.jaspring.signals.internal.SignalNode;

/// Factory for every reactive primitive, mirroring `@angular/core`.
public final class Signals {

    private Signals() {}

    public static <T> WritableSignal<T> signal(T initial) {
        return signal(initial, Equality.defaultEquality());
    }

    public static <T> WritableSignal<T> signal(T initial, Equality<T> equality) {
        return new SignalNode<>(initial, equality, SignalOwner.current());
    }

    public static <T> Signal<T> computed(Supplier<T> computation) {
        return computed(computation, Equality.defaultEquality());
    }

    public static <T> Signal<T> computed(Supplier<T> computation, Equality<T> equality) {
        return new ComputedNode<>(computation, equality);
    }

    /// Shorthand form: the computation is also the source, so any explicit `set` is replaced when it changes.
    public static <T> WritableSignal<T> linkedSignal(Supplier<T> computation) {
        return linkedSignal(computation, (source, previous) -> source, Equality.defaultEquality());
    }

    public static <S, T> WritableSignal<T> linkedSignal(Supplier<S> source, LinkedComputation<S, T> computation) {
        return linkedSignal(source, computation, Equality.defaultEquality());
    }

    public static <S, T> WritableSignal<T> linkedSignal(
            Supplier<S> source, LinkedComputation<S, T> computation, Equality<T> equality) {
        return new LinkedSignalNode<>(source, computation, equality, SignalOwner.current());
    }

    public static EffectRef effect(Runnable body) {
        return effect(cleanup -> body.run());
    }

    public static EffectRef effect(Consumer<CleanupRegistrar> body) {
        return effect(body, EffectContext.require());
    }

    public static EffectRef effect(Runnable body, EffectScheduler scheduler) {
        return effect(cleanup -> body.run(), scheduler);
    }

    public static EffectRef effect(Consumer<CleanupRegistrar> body, EffectScheduler scheduler) {
        if (Graph.activeConsumer() != null) {
            throw new IllegalStateException("Call effect() outside of a reactive context, for example in the constructor");
        }
        final var node = new EffectNode(body, scheduler);
        scheduler.add(node);
        return node;
    }

    public static <T> T untracked(Supplier<T> body) {
        return Graph.untracked(body);
    }

    public static void untracked(Runnable body) {
        Graph.untracked(() -> {
            body.run();
            return null;
        });
    }

    public static <T> InputSignal<T> input() {
        return input(null);
    }

    public static <T> InputSignal<T> input(T initial) {
        return InputSignalNode.optional(initial, null);
    }

    public static <T> InputSignal<T> input(T initial, Function<Object, T> transform) {
        return InputSignalNode.optional(initial, transform);
    }

    public static <T> InputSignal<T> inputRequired() {
        return InputSignalNode.required(null);
    }

    public static <T> InputSignal<T> inputRequired(Function<Object, T> transform) {
        return InputSignalNode.required(transform);
    }

    public static <T> ModelSignal<T> model() {
        return model(null);
    }

    public static <T> ModelSignal<T> model(T initial) {
        return ModelSignalNode.optional(initial, SignalOwner.current());
    }

    public static <T> ModelSignal<T> modelRequired() {
        return ModelSignalNode.required(SignalOwner.current());
    }

    public static <T> OutputEmitter<T> output() {
        return new OutputEmitter<>();
    }
}
