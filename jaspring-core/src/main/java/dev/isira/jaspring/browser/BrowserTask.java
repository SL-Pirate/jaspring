package dev.isira.jaspring.browser;

import java.io.Serializable;

/// Code a component hands to `Platform.onBrowser`: it runs in the browser, compiled to WebAssembly at build time.
///
/// The lambda must be self-contained: no `this`, no Spring beans, no signals. Values it captures are copied into
/// the page as JSON, so they must be records, enums, strings, numbers, booleans or arrays of those (collections
/// only inside records). The interface is `Serializable` so the server can identify the lambda and read its
/// captured values without a compiler plugin.
@FunctionalInterface
public interface BrowserTask<R> extends Serializable {

    R run(BrowserClient client);
}
