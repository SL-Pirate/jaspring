package dev.isira.jaspring.signals;

@FunctionalInterface
public interface Subscription {

    void unsubscribe();
}
