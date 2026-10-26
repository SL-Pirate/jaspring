package dev.isira.jaspring.component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.isira.jaspring.signals.InputSignal;
import dev.isira.jaspring.signals.ModelSignal;
import dev.isira.jaspring.signals.OutputRef;
import dev.isira.jaspring.signals.internal.InputSignalNode;
import dev.isira.jaspring.signals.internal.ModelSignalNode;
import dev.isira.jaspring.template.selector.CssSelector;

/// Reads `@Component` metadata and the input/output members of a class.
public final class ComponentDefs {

    private ComponentDefs() {}

    public static ComponentDef of(Class<?> type, int index) {
        final var annotation = type.getAnnotation(Component.class);
        if (annotation == null) {
            throw new IllegalArgumentException(type.getName() + " is not annotated with @Component");
        }
        final var inputs = new LinkedHashMap<String, InputDef>();
        final var outputs = new LinkedHashMap<String, OutputDef>();
        for (final var field : fields(type)) {
            final var input = field.getAnnotation(Input.class);
            final var output = field.getAnnotation(Output.class);
            if (InputSignal.class.isAssignableFrom(field.getType())) {
                final var name = input != null && !input.value().isEmpty() ? input.value() : field.getName();
                inputs.put(name, new InputDef(name, InputDef.Kind.SIGNAL, false, field));
            } else if (ModelSignal.class.isAssignableFrom(field.getType())) {
                final var name = input != null && !input.value().isEmpty() ? input.value() : field.getName();
                inputs.put(name, new InputDef(name, InputDef.Kind.MODEL, false, field));
                outputs.put(name + "Change", new OutputDef(name + "Change", field));
            } else if (input != null) {
                final var name = input.value().isEmpty() ? field.getName() : input.value();
                inputs.put(name, new InputDef(name, InputDef.Kind.FIELD, input.required(), field));
            } else if (OutputRef.class.isAssignableFrom(field.getType())) {
                final var name = output != null && !output.value().isEmpty() ? output.value() : field.getName();
                outputs.put(name, new OutputDef(name, field));
            } else if (output != null) {
                throw new IllegalStateException("@Output field " + field.getName() + " on " + type.getSimpleName() + " must be an OutputEmitter");
            }
        }
        final var templateSource = annotation.template().isEmpty() ? null : annotation.template();
        if (templateSource == null && annotation.templateUrl().isEmpty()) {
            throw new IllegalStateException("Component " + type.getSimpleName() + " needs a template or a templateUrl");
        }
        if (templateSource != null && !annotation.templateUrl().isEmpty()) {
            throw new IllegalStateException("Component " + type.getSimpleName() + " cannot have both template and templateUrl");
        }
        final var styles = new ArrayList<>(List.of(annotation.styles()));
        if (!annotation.styleUrl().isEmpty()) {
            styles.add(resource(type, annotation.styleUrl()));
        }
        return new ComponentDef(type, annotation.selector(), CssSelector.parse(annotation.selector()), annotation.changeDetection(),
                java.util.Collections.unmodifiableMap(inputs), java.util.Collections.unmodifiableMap(outputs), Set.of(annotation.schemas()), annotation.preserveWhitespaces(),
                templateSource != null ? templateSource : resource(type, annotation.templateUrl()),
                templateSource != null ? type.getSimpleName() + ".template" : annotation.templateUrl(),
                List.copyOf(styles), index);
    }

    /// Signal members learn their names after construction; field initializers cannot see the field name.
    public static void bindSignalNames(Object instance) {
        for (final var field : fields(instance.getClass())) {
            try {
                if (InputSignalNode.class.isAssignableFrom(field.getType()) || InputSignal.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    if (field.get(instance) instanceof InputSignalNode<?> node) node.bindName(field.getName());
                } else if (ModelSignal.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    if (field.get(instance) instanceof ModelSignalNode<?> node) node.bindName(field.getName());
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    static List<Field> fields(Class<?> type) {
        final var fields = new ArrayList<Field>();
        for (var current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (final var field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) fields.add(field);
            }
        }
        return fields;
    }

    /// Resolves `templateUrl` / `styleUrl` relative to the component's package, like Angular resolves them relative to
    /// the file: `card.html`, `./card.html` and `../shared/card.html` all work, and a leading `/` is classpath-absolute.
    static String resource(Class<?> type, String url) {
        final var path = resourcePath(type, url);
        try (var in = type.getResourceAsStream("/" + path)) {
            if (in == null) {
                throw new IllegalStateException("Resource '" + url + "' for " + type.getSimpleName() + " not found at classpath:"
                        + path + " (templateUrl and styleUrl are relative to the component's package)");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // `.` and `..` are folded here because jar lookups take resource names literally.
    static String resourcePath(Class<?> type, String url) {
        final var segments = new ArrayDeque<String>();
        if (!url.startsWith("/")) {
            for (final var part : type.getPackageName().split("\\.")) {
                if (!part.isEmpty()) segments.add(part);
            }
        }
        for (final var part : url.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (!part.equals("..")) {
                segments.add(part);
            } else if (segments.pollLast() == null) {
                throw new IllegalStateException("Resource '" + url + "' for " + type.getSimpleName() + " points above the classpath root");
            }
        }
        return String.join("/", segments);
    }
}
