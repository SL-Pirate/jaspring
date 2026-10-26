package dev.isira.jaspring.boot.testapp.components;

import java.util.List;
import java.util.concurrent.TimeUnit;

import dev.isira.jaspring.cd.lifecycle.OnInit;
import dev.isira.jaspring.component.Component;
import dev.isira.jaspring.signals.Signals;
import dev.isira.jaspring.signals.WritableSignal;
import dev.isira.jaspring.zone.Zone;

@Component(selector = "app-async", template = "@if (users() === null) {<em>loading</em>} @else {<ul>@for (u of users(); track u) {<li>{{ u }}</li>}</ul>}")
public class AsyncComponent implements OnInit {

    public static volatile long delayMillis = 50;

    public WritableSignal<List<String>> users = Signals.signal(null);

    @Override
    public void ngOnInit() {
        Zone.current().fork(() -> {
            TimeUnit.MILLISECONDS.sleep(delayMillis);
            return List.of("ann", "bob");
        }).thenAccept(users::set);
    }
}
