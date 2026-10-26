package dev.isira.jaspring.boot.testapp.components;

import dev.isira.jaspring.component.Component;
import dev.isira.jaspring.signals.Signals;
import dev.isira.jaspring.signals.WritableSignal;

@Component(selector = "app-counter", template = "<button (click)=\"count.set(count() + 1)\">+</button><span>{{ count() }}</span>",
        styles = "span { font-weight: bold }")
public class CounterComponent {

    public WritableSignal<Integer> count = Signals.signal(0);
}
