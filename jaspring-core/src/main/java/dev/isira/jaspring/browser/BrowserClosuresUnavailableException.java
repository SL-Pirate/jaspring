package dev.isira.jaspring.browser;

/// `Platform.onBrowser` cannot run the closure: no WebAssembly build is on the classpath, the build was skipped,
/// or it is stale. The message says which and how to fix it.
public class BrowserClosuresUnavailableException extends IllegalStateException {

    public BrowserClosuresUnavailableException(String message) {
        super(message);
    }
}
