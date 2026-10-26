package dev.isira.jaspring.sample.components;

import dev.isira.jaspring.component.Component;
import dev.isira.jaspring.signals.Signal;
import dev.isira.jaspring.signals.Signals;
import dev.isira.jaspring.signals.WritableSignal;

@Component(selector = "app-counter", templateUrl = "counter.component.html", styles = """
        .count { @apply mx-4 text-3xl font-bold tabular-nums; }
        """)
public class CounterComponent {

    public final WritableSignal<Integer> count = Signals.signal(0);
    public final Signal<Integer> doubled = Signals.computed(() -> count.get() * 2);

    public void reset() {
        count.set(0);
    }
}
