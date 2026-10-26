package dev.isira.jaspring.signals;

/// What a scheduler needs from an effect: whether it has pending work and how to run it.
public interface SchedulableEffect {

    boolean isDirty();

    void run();
}
