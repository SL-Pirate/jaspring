package dev.isira.jaspring.live;

import java.util.List;

/// The result of one turn: patches for the topmost changed views, and an optional redirect.
public record PatchSet(List<Patch> patches, String redirect) {

    public static final PatchSet EMPTY = new PatchSet(List.of(), null);

    public boolean isEmpty() {
        return patches.isEmpty() && redirect == null;
    }
}
