package dev.isira.jaspring.browser;

/// A browser call failed: the client reported an error, the call timed out, or the page has no live transport.
public class BrowserCallException extends RuntimeException {

    public BrowserCallException(String message) {
        super(message);
    }
}
