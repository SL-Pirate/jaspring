package dev.isira.jaspring.browser;

import java.util.List;
import java.util.Optional;

/// A DOM element inside the component's host element.
public interface Element {

    Optional<Element> query(String selector);

    List<Element> queryAll(String selector);

    Optional<String> attribute(String name);

    void attribute(String name, String value);

    void removeAttribute(String name);

    boolean hasClass(String name);

    void addClass(String... names);

    void removeClass(String... names);

    void toggleClass(String name, boolean on);

    String text();

    void text(String text);

    /// The `value` property of form controls; empty for other elements.
    String value();

    void value(String value);

    void focus();

    void blur();

    void scrollIntoView();

    ElementBounds bounds();

    /// Dispatches a bubbling `CustomEvent`; a template binding `(type)="..."` on this element or an ancestor
    /// receives it, with `detail` under `$event.detail`.
    void dispatch(String type);

    void dispatch(String type, String detail);
}
