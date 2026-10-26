package dev.isira.jaspring.sample.models;

public record Todo(long id, String title, boolean done) {

    public Todo toggle() {
        return new Todo(id, title, !done);
    }
}
