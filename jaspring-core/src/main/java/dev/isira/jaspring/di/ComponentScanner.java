package dev.isira.jaspring.di;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

import dev.isira.jaspring.component.Component;
import dev.isira.jaspring.component.Directive;
import dev.isira.jaspring.component.Pipe;

/// Finds `@Component`, `@Pipe` and `@Directive` classes with Spring's classpath scanner.
public final class ComponentScanner {

    public record Found(List<Class<?>> components, List<Class<?>> pipes, List<Class<?>> directives) {}

    private final ClassLoader classLoader;

    public ComponentScanner(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public Found scan(List<String> basePackages) {
        return new Found(scan(basePackages, Component.class), scan(basePackages, Pipe.class), scan(basePackages, Directive.class));
    }

    private List<Class<?>> scan(List<String> basePackages, Class<? extends java.lang.annotation.Annotation> annotation) {
        final var provider = new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AnnotationTypeFilter(annotation));
        final var found = new ArrayList<Class<?>>();
        for (final var basePackage : basePackages) {
            for (final var candidate : provider.findCandidateComponents(basePackage)) {
                found.add(ClassUtils.resolveClassName(candidate.getBeanClassName(), classLoader));
            }
        }
        found.sort(java.util.Comparator.comparing(Class::getName));
        return List.copyOf(found);
    }
}
