package dev.isira.jaspring.boot;

import java.util.Map;

/// The scoped CSS of every component that declares styles, keyed by component class.
public record ComponentStyles(Map<Class<?>, String> byComponent) {

    public String allAsStyleBlock() {
        if (byComponent.isEmpty()) return "";
        final var sb = new StringBuilder("<style data-jsp-styles>\n");
        byComponent.forEach((type, css) -> sb.append("/* ").append(type.getSimpleName()).append(" */\n").append(css).append('\n'));
        return sb.append("</style>").toString();
    }
}
