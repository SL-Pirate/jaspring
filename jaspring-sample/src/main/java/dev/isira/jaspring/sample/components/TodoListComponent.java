package dev.isira.jaspring.sample.components;

import java.util.List;

import dev.isira.jaspring.component.Component;
import dev.isira.jaspring.sample.models.Todo;
import dev.isira.jaspring.sample.services.TodoService;

@Component(selector = "app-todo-list", templateUrl = "todo-list.component.html", styles = """
        li.done span { text-decoration: line-through; color: #888; }
        form { display: flex; gap: .5rem; }
        """)
public class TodoListComponent {

    private final TodoService todos;
    public String draft = "";

    public TodoListComponent(TodoService todos) {
        this.todos = todos;
    }

    public List<Todo> todos() {
        return todos.all();
    }

    public long remaining() {
        return todos.all().stream().filter(todo -> !todo.done()).count();
    }

    public void add() {
        if (draft.isBlank()) return;
        todos.add(draft.trim());
        draft = "";
    }

    public void toggle(Todo todo) {
        todos.toggle(todo.id());
    }

    public void remove(Todo todo) {
        todos.remove(todo.id());
    }
}
