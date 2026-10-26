package dev.isira.jaspring.sample.services.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import dev.isira.jaspring.sample.models.Todo;
import dev.isira.jaspring.sample.services.TodoService;

@Service
@Scope("jaspring-page")
public class InMemoryTodoService implements TodoService {

    private final List<Todo> todos = new ArrayList<>(List.of(new Todo(1, "Write a Java component", true), new Todo(2, "Reuse the Angular template", false)));
    private long nextId = 3;

    @Override
    public List<Todo> all() {
        return List.copyOf(todos);
    }

    @Override
    public Todo add(String title) {
        final var todo = new Todo(nextId++, title, false);
        todos.add(todo);
        return todo;
    }

    @Override
    public void toggle(long id) {
        todos.replaceAll(todo -> todo.id() == id ? todo.toggle() : todo);
    }

    @Override
    public void remove(long id) {
        todos.removeIf(todo -> todo.id() == id);
    }
}
