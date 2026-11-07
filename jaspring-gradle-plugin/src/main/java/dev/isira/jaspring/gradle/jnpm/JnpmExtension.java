package dev.isira.jaspring.gradle.jnpm;

import java.net.URI;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;

/// npm dependencies owned by one application module. Runtime and declaration scopes are deliberately separate;
/// unlike Java's `api`, declarations here do not publish dependencies to other Gradle projects.
public abstract class JnpmExtension {

    private final NamedDomainObjectContainer<Registry> registries;

    @Inject
    public JnpmExtension(ObjectFactory objects) {
        getImplementation().convention(Map.of());
        getApi().convention(Map.of());
        getDevImplementation().convention(Map.of());
        getDevApi().convention(Map.of());
        getLint().getEnabled().convention(true);
        registries = objects.domainObjectContainer(Registry.class, name -> objects.newInstance(Registry.class, name));
    }

    public abstract MapProperty<String, String> getImplementation();
    public abstract MapProperty<String, String> getApi();
    public abstract MapProperty<String, String> getDevImplementation();
    public abstract MapProperty<String, String> getDevApi();

    /// Optional preinstalled Node distribution, primarily for offline and centrally managed toolchains.
    public abstract DirectoryProperty getNodeHome();

    @Nested
    public abstract Lint getLint();

    public void lint(Action<? super Lint> action) { action.execute(getLint()); }
    public void implementation(String notation) { add(getImplementation(), notation); }
    public void api(String notation) { add(getApi(), notation); }
    public void devImplementation(String notation) { add(getDevImplementation(), notation); }
    public void devApi(String notation) { add(getDevApi(), notation); }

    public NamedDomainObjectContainer<Registry> getRegistries() { return registries; }
    public void registries(Action<? super NamedDomainObjectContainer<Registry>> action) { action.execute(registries); }

    private static void add(MapProperty<String, String> target, String notation) {
        final var separator = notation.lastIndexOf(':');
        if (separator <= 0 || separator == notation.length() - 1) {
            throw new IllegalArgumentException("Expected an npm dependency like 'lodash-es:^4.17.21' or '@scope/package:1.2.3'");
        }
        final var name = notation.substring(0, separator);
        final var version = notation.substring(separator + 1).trim();
        if (!name.matches("(?:@[a-z0-9._-]+/)?[a-z0-9._-]+") || !version.matches("[0-9vVxX*~^<>=][0-9A-Za-z*~^<>=|. +\\-]*")
                || !version.matches("[~^<>= ]*(?:[vV]?[0-9].*|[xX*](?:[ .|].*)?)")) {
            throw new IllegalArgumentException("JNPM supports registry package names and semver versions/ranges only: " + notation);
        }
        final var previous = target.get().get(name);
        if (previous != null && !previous.equals(version)) throw new IllegalArgumentException("Conflicting JNPM declarations for " + name);
        target.put(name, version);
    }

    public abstract static class Lint {
        public abstract Property<Boolean> getEnabled();
    }

    /// A registry without scopes replaces npmjs.org; scoped registries only serve their listed scopes.
    public abstract static class Registry implements Named {
        private final String name;

        @Inject
        public Registry(String name) {
            this.name = name;
            getScopes().convention(List.of());
        }

        @Override
        public String getName() { return name; }
        public abstract Property<URI> getUrl();
        public abstract ListProperty<String> getScopes();
        public abstract Property<String> getToken();
    }
}
