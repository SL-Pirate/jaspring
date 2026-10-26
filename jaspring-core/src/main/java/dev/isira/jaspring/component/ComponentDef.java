package dev.isira.jaspring.component;

import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.isira.jaspring.template.selector.CssSelector;

/// Everything the framework knows about a component class before its template is compiled.
public record ComponentDef(
        Class<?> type,
        String selector,
        List<CssSelector> selectors,
        ChangeDetectionStrategy changeDetection,
        Map<String, InputDef> inputs,
        Map<String, OutputDef> outputs,
        Set<Schema> schemas,
        boolean preserveWhitespaces,
        String templateSource,
        String templateUrl,
        List<String> styles,
        int index) {

    public String name() {
        return type.getSimpleName();
    }

    /// The scoping id used by emulated encapsulation: `_nghost-jsp-c<index>` / `_ngcontent-jsp-c<index>`.
    public String scopeId() {
        return "jsp-c" + index;
    }
}
