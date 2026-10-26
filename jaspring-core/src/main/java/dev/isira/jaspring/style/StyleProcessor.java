package dev.isira.jaspring.style;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.isira.jaspring.component.ComponentDef;
import dev.isira.jaspring.component.ComponentRegistry;
import dev.isira.jaspring.template.parse.AngularCompilerRuntime;

/// Runs each component's stylesheets through PostCSS with the Jaspring scope plugin at startup, producing the
/// emulated-encapsulation CSS (`[_ngcontent-jsp-cN]`, `[_nghost-jsp-cN]`) that the layout emits in `<head>`.
public final class StyleProcessor {

    private final AngularCompilerRuntime runtime;

    public StyleProcessor(AngularCompilerRuntime runtime) {
        this.runtime = runtime;
    }

    public String process(ComponentDef def) {
        final var out = new StringBuilder();
        for (final var css : def.styles()) {
            if (!out.isEmpty()) out.append('\n');
            out.append(runtime.processCss(css, def.scopeId()));
        }
        return out.toString();
    }

    /// Processed CSS per component class, in registration order, for every component that declares styles.
    public Map<Class<?>, String> processAll(ComponentRegistry registry) {
        final var styles = new LinkedHashMap<Class<?>, String>();
        for (final var def : registry.components()) {
            if (!def.styles().isEmpty()) styles.put(def.type(), process(def));
        }
        return styles;
    }
}
