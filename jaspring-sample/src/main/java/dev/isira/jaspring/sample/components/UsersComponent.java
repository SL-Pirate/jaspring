package dev.isira.jaspring.sample.components;

import java.util.List;

import dev.isira.jaspring.cd.lifecycle.OnInit;
import dev.isira.jaspring.component.Component;
import dev.isira.jaspring.sample.services.UserDirectory;
import dev.isira.jaspring.signals.Signals;
import dev.isira.jaspring.signals.WritableSignal;
import dev.isira.jaspring.zone.Zone;

/// The directory is slow, so the lookup is forked inside the zone: the first response shows "loading",
/// and the list arrives as a pushed patch when the fork completes.
@Component(selector = "app-users", templateUrl = "users.component.html")
public class UsersComponent implements OnInit {

    private final UserDirectory directory;
    public final WritableSignal<List<String>> users = Signals.signal(null);
    public final WritableSignal<String> chosen = Signals.signal("nobody");
    public int parentRenders;

    public UsersComponent(UserDirectory directory) {
        this.directory = directory;
    }

    @Override
    public void ngOnInit() {
        Zone.current().fork(directory::findAll).thenAccept(users::set);
    }

    public int renderCount() {
        return ++parentRenders;
    }
}
