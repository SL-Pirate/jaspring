package dev.isira.jaspring.browser;

import java.util.List;
import java.util.Optional;

/// `localStorage` or `sessionStorage`.
public interface Storage {

    Optional<String> get(String key);

    void set(String key, String value);

    void remove(String key);

    void clear();

    List<String> keys();
}
