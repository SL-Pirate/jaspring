package dev.isira.jaspring.live;

/// The new outer HTML of one component view, identified by the `data-jsp-v` id on its host element.
public record Patch(long viewId, String html) {}
