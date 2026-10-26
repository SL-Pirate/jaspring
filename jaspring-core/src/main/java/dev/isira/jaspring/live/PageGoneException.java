package dev.isira.jaspring.live;

/// The page was destroyed or never existed for this session; the shim reloads when it sees this.
public final class PageGoneException extends RuntimeException {

    public PageGoneException(String pageId) {
        super("Live page " + pageId + " is gone");
    }
}
