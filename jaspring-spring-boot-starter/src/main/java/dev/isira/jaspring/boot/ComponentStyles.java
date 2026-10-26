package dev.isira.jaspring.boot;

import java.util.Map;

/// The scoped CSS of every component that declares styles, keyed by component class, plus the global
/// stylesheet (Tailwind's output) when one is configured.
public record ComponentStyles(Map<Class<?>, String> byComponent, String global) {

    public ComponentStyles(Map<Class<?>, String> byComponent) {
        this(byComponent, "");
    }

    public String allAsStyleBlock() {
        final var sb = new StringBuilder();
        if (!global.isBlank()) {
            sb.append("<style data-jsp-tailwind>\n").append(global).append("\n</style>\n");
        }
        if (!byComponent.isEmpty()) {
            sb.append("<style data-jsp-styles>\n");
            byComponent.forEach((type, css) -> sb.append("/* ").append(type.getSimpleName()).append(" */\n").append(css).append('\n'));
            sb.append("</style>");
        }
        return sb.toString();
    }
}
