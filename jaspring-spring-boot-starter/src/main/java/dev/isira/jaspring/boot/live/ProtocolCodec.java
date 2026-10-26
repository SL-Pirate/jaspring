package dev.isira.jaspring.boot.live;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.json.JsonMapper;

import dev.isira.jaspring.live.PatchSet;

/// The JSON wire protocol shared by both transports (see docs/protocol.md).
public final class ProtocolCodec {

    private final JsonMapper mapper = JsonMapper.builder().build();

    public record ClientMessage(String type, String page, String handler, Object event) {}

    @SuppressWarnings("unchecked")
    public ClientMessage decode(String json) {
        final var map = mapper.readValue(json, Map.class);
        return new ClientMessage(string(map.get("t")), string(map.get("page")), string(map.get("h")), map.get("ev"));
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    public String encode(Map<String, Object> message) {
        return mapper.writeValueAsString(message);
    }

    public String encodeAll(List<Map<String, Object>> messages) {
        return mapper.writeValueAsString(messages);
    }

    public List<Map<String, Object>> messages(PatchSet patches, long seq) {
        final var messages = new ArrayList<Map<String, Object>>();
        if (!patches.patches().isEmpty()) {
            final var list = new ArrayList<Map<String, Object>>();
            for (final var patch : patches.patches()) {
                final var entry = new LinkedHashMap<String, Object>();
                entry.put("v", patch.viewId());
                entry.put("html", patch.html());
                list.add(entry);
            }
            final var message = new LinkedHashMap<String, Object>();
            message.put("t", "patch");
            message.put("seq", seq);
            message.put("patches", list);
            messages.add(message);
        }
        if (patches.redirect() != null) {
            messages.add(Map.of("t", "redirect", "url", patches.redirect()));
        }
        return messages;
    }

    public Map<String, Object> error(String code, String message) {
        final var error = new LinkedHashMap<String, Object>();
        error.put("t", "error");
        error.put("code", code);
        error.put("message", message);
        return error;
    }

    public Map<String, Object> ping() {
        return Map.of("t", "ping");
    }
}
