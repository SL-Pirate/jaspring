package dev.isira.jaspring.live;

/// The browser's answer to a call: `value` on success, otherwise the client's `error` message.
public record BrowserResult(String callId, Object value, String error) {}
