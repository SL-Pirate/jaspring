package dev.isira.jaspring.sample.services;

import java.util.List;

import dev.isira.jaspring.sample.models.Todo;

/// Per-page todo state (`jaspring-page` scoped), so every browser tab has its own list.
public interface TodoService {

    List<Todo> all();

    Todo add(String title);

    void toggle(long id);

    void remove(long id);
}
