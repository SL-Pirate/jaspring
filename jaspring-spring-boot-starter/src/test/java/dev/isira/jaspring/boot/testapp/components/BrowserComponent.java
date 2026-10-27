package dev.isira.jaspring.boot.testapp.components;

import dev.isira.jaspring.browser.Browser;
import dev.isira.jaspring.component.Component;
import dev.isira.jaspring.signals.Signals;
import dev.isira.jaspring.signals.WritableSignal;

/// Calls the browser from an event handler.
@Component(selector = "app-browser", template = "<button (click)=\"measure()\">m</button><b>{{ width() }}</b>")
public class BrowserComponent {

    public WritableSignal<String> width = Signals.signal("?");

    public void measure() {
        Browser.current().call("viewport").whenComplete((value, error) ->
                width.set(error == null ? String.valueOf(value) : "error:" + error.getMessage()));
    }
}
