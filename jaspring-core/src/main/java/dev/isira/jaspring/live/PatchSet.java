package dev.isira.jaspring.live;

import java.util.List;

/// The result of one turn: patches for the topmost changed views, an optional redirect, and the browser calls
/// the turn issued. Calls follow the patches so a hook mounted by a patch exists before a call targets it.
public record PatchSet(List<Patch> patches, String redirect, List<BrowserCall> calls) {

    public static final PatchSet EMPTY = new PatchSet(List.of(), null, List.of());

    public PatchSet(List<Patch> patches, String redirect) {
        this(patches, redirect, List.of());
    }

    public boolean isEmpty() {
        return patches.isEmpty() && redirect == null && calls.isEmpty();
    }
}
