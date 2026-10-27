package dev.isira.jaspring.browser;

import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

import dev.isira.jaspring.live.LivePage;
import dev.isira.jaspring.view.ComponentView;
import dev.isira.jaspring.zone.ZoneTask;

/// Calls into the browser from a component. Obtain it with `Browser.current()` inside a turn: in a constructor,
/// a lifecycle hook, an event handler or a fork continuation.
public final class Browser {

    private final LivePage page;
    private final ComponentView view;

    private Browser(LivePage page, ComponentView view) {
        this.page = page;
        this.view = view;
    }

    /// The browser of the page whose turn runs on this thread. The current component, when there is one, scopes
    /// hook targets to its host element.
    public static Browser current() {
        return new Browser(LivePage.current(), ComponentView.current());
    }

    /// Invokes `target` in the browser and completes, inside a page turn, with the JSON value it returned: a
    /// `Map`, `List`, `String`, `Number`, `Boolean` or `null`. The cast to `T` is unchecked.
    ///
    /// A dotted target (`hook.method`) runs a method of the hook mounted inside this component's host element;
    /// a bare name runs a function registered with `jaspring.calls.register`. The task fails with
    /// `BrowserCallException` when the client reports an error, after the call timeout, or when live events are off.
    public <T> ZoneTask<T> call(String target, Object... args) {
        Objects.requireNonNull(target, "target");
        final var hookTarget = target.indexOf('.') >= 0;
        if (hookTarget && view == null) {
            throw new IllegalStateException("Browser call '" + target + "' targets a hook but no component is current;"
                    + " obtain Browser.current() in the constructor, a hook or a handler of the component that owns the hook");
        }
        final var arguments = Collections.unmodifiableList(Arrays.asList(args.clone()));
        return page.callBrowser(target, hookTarget ? view.id() : null, arguments);
    }

    <R> ZoneTask<R> closure(BrowserTask<?> task, Class<R> promiseType) {
        return ClosureCalls.invoke(page, view, task, promiseType);
    }
}
