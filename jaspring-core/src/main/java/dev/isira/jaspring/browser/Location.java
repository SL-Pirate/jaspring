package dev.isira.jaspring.browser;

/// `window.location`. Only the hash may be written; navigation belongs to the server (`LivePage.redirect`).
public interface Location {

    String href();

    String pathname();

    String search();

    String hash();

    void hash(String fragment);
}
