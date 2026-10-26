package dev.isira.jaspring.sample.components;

import dev.isira.jaspring.component.ChangeDetectionStrategy;
import dev.isira.jaspring.component.Component;
import dev.isira.jaspring.signals.InputSignal;
import dev.isira.jaspring.signals.OutputEmitter;
import dev.isira.jaspring.signals.Signals;

/// OnPush: re-renders only when its inputs change, an event fires inside it, or a signal it reads changes.
@Component(selector = "app-user-card", templateUrl = "user-card.component.html", changeDetection = ChangeDetectionStrategy.ON_PUSH, styles = """
        :host { display: block; border: 1px solid #ccc; border-radius: .5rem; padding: .75rem; margin: .5rem 0; }
        .renders { color: #888; font-size: .8rem; }
        """)
public class UserCardComponent {

    public final InputSignal<String> name = Signals.inputRequired();
    public final InputSignal<String> role = Signals.input("member");
    public final OutputEmitter<String> selected = Signals.output();
    public int renders;

    public int renderCount() {
        return ++renders;
    }

    public void choose() {
        selected.emit(name.get());
    }
}
