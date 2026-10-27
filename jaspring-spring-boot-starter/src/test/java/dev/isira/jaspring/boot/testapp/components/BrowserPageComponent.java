package dev.isira.jaspring.boot.testapp.components;

import dev.isira.jaspring.browser.Browser;
import dev.isira.jaspring.cd.lifecycle.OnInit;
import dev.isira.jaspring.component.Component;
import dev.isira.jaspring.signals.Signals;
import dev.isira.jaspring.signals.WritableSignal;

/// Calls the browser while the page is first rendered, so the call must travel with the document.
@Component(selector = "app-browser-page", template = "<b>{{ width() }}</b>")
public class BrowserPageComponent implements OnInit {

    public WritableSignal<String> width = Signals.signal("?");

    @Override
    public void ngOnInit() {
        Browser.current().call("viewport").whenComplete((value, error) ->
                width.set(error == null ? String.valueOf(value) : "error:" + error.getMessage()));
    }
}
