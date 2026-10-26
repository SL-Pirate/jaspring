package dev.isira.jaspring.sample.services;

import java.util.List;

/// A slow lookup, to show zone-forked loading.
public interface UserDirectory {

    List<String> findAll() throws InterruptedException;
}
