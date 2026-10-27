package dev.isira.jaspring.live;

import java.util.List;

/// One server-to-browser call. A dotted `target` (`hook.method`) is resolved inside the host element of view
/// `viewId`; a bare target is a page-level function, and `viewId` is null.
public record BrowserCall(String id, String target, Long viewId, List<Object> args) {}
