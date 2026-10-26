package dev.isira.jaspring.style;

import java.util.Collection;
import java.util.List;

import dev.isira.jaspring.template.parse.AngularCompilerRuntime;

/// Runs Tailwind's engine inside the JavaScript runtime at startup. The global stylesheet gets utilities for the
/// class names found in the templates; component styles get `@apply` and theme variables resolved before scoping.
public final class TailwindProcessor {

    private final AngularCompilerRuntime runtime;
    private final String stylesheet;

    /// @param stylesheet the application's Tailwind CSS, normally `@import "tailwindcss";` plus `@theme` customizations
    public TailwindProcessor(AngularCompilerRuntime runtime, String stylesheet) {
        this.runtime = runtime;
        this.stylesheet = stylesheet.contains("@import") ? stylesheet : "@import \"tailwindcss\";\n" + stylesheet;
    }

    /// The global CSS: preflight, theme variables and the utilities the candidates use.
    public String global(Collection<String> candidates) {
        return runtime.compileTailwind(stylesheet, candidates);
    }

    /// Resolves `@apply` and theme references inside one component's stylesheet without emitting utilities.
    public String componentStyle(String css) {
        if (!css.contains("@apply") && !css.contains("--")) return css;
        return runtime.compileTailwind("@reference \"jaspring:app\";\n" + css, List.of(), stylesheet);
    }
}
