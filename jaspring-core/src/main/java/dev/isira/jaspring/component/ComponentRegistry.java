package dev.isira.jaspring.component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.isira.jaspring.pipe.PipeRegistry;
import dev.isira.jaspring.pipe.PipeTransform;
import dev.isira.jaspring.template.compile.CompiledTemplate;
import dev.isira.jaspring.template.compile.TemplateCompiler;
import dev.isira.jaspring.template.selector.CssSelector;

/// All components, pipes and directives of the application. Everything registered is visible to every template,
/// which is the one deliberate deviation from Angular's standalone `imports`.
public final class ComponentRegistry implements PipeRegistry {

    private final Map<Class<?>, ComponentDef> components = new LinkedHashMap<>();
    private final Map<Class<?>, CompiledTemplate> templates = new LinkedHashMap<>();
    private final Map<String, PipeDef> pipeDefs = new LinkedHashMap<>();
    private final Map<String, PipeTransform> pipes = new LinkedHashMap<>();
    private final ComponentFactory factory;

    public ComponentRegistry(ComponentFactory factory) {
        this.factory = factory;
    }

    public ComponentDef registerComponent(Class<?> type) {
        return components.computeIfAbsent(type, t -> ComponentDefs.of(t, components.size()));
    }

    public PipeDef registerPipe(Class<?> type) {
        final var def = PipeDef.of(type);
        pipeDefs.put(def.name(), def);
        pipes.put(def.name(), (PipeTransform) factory.create(type));
        return def;
    }

    public void registerPipe(String name, PipeTransform pipe) {
        pipeDefs.put(name, new PipeDef(name, true, pipe.getClass()));
        pipes.put(name, pipe);
    }

    /// Compiles every registered template. Fails fast with all errors of the first failing component.
    public void compileAll(TemplateCompiler compiler) {
        for (final var def : components.values()) {
            templates.computeIfAbsent(def.type(), t -> compiler.compile(def, this));
        }
    }

    public ComponentDef component(Class<?> type) {
        final var def = components.get(type);
        if (def == null) throw new IllegalArgumentException(type.getSimpleName() + " is not a registered component");
        return def;
    }

    public CompiledTemplate template(Class<?> type) {
        final var template = templates.get(type);
        if (template == null) throw new IllegalStateException(type.getSimpleName() + " has not been compiled");
        return template;
    }

    public List<ComponentDef> components() {
        return List.copyOf(components.values());
    }

    /// The component whose selector matches an element, or `null`. Angular rejects two components on one element.
    public ComponentDef match(String tagName, Map<String, String> attributes, Set<String> boundNames) {
        final var candidate = CssSelector.forElement(tagName, attributes, boundNames);
        final var matches = new ArrayList<ComponentDef>();
        for (final var def : components.values()) {
            if (def.selectors().stream().anyMatch(selector -> selector.matches(candidate))) matches.add(def);
        }
        if (matches.size() > 1) {
            throw new IllegalStateException("Multiple components match node with tagname " + tagName + ": "
                    + matches.stream().map(ComponentDef::name).toList());
        }
        return matches.isEmpty() ? null : matches.get(0);
    }

    public boolean hasPipe(String name) {
        return pipes.containsKey(name);
    }

    public PipeDef pipeDef(String name) {
        return pipeDefs.get(name);
    }

    @Override
    public PipeTransform get(String name) {
        return pipes.get(name);
    }

    public ComponentFactory factory() {
        return factory;
    }
}
