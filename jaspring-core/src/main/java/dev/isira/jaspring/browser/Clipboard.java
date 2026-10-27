package dev.isira.jaspring.browser;

/// `navigator.clipboard`. Browsers allow it only in secure contexts and, for reading, after a user gesture.
public interface Clipboard {

    BrowserPromise<Void> writeText(String text);

    BrowserPromise<String> readText();
}
