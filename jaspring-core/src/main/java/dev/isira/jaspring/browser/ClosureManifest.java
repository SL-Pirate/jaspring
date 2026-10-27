package dev.isira.jaspring.browser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// What the Gradle plugin recorded about the WebAssembly build (`jaspring/jaspring-browser.json`): every compiled
/// closure, where it was found, and the assets to ship. `EMPTY` means no build exists.
public final class ClosureManifest {

    public enum Timing { MOUNT, EVENT }

    /// `implClass` is the dotted class name; `implSignature` the JVM descriptor of the synthetic lambda method;
    /// `classSha256` the digest of the class file the closure was compiled from.
    public record Closure(String id, String implClass, String implMethod, String implSignature, List<String> captured,
            String returnType, boolean promise, Timing timing, String enclosingMethod, String classSha256) {}

    public record Asset(String name, String sha256, long size) {}

    public record Assets(Asset js, Asset wasm) {}

    public static final ClosureManifest EMPTY = new ClosureManifest(null, null, List.of());

    private final String graalvm;
    private final Assets assets;
    private final Map<String, Closure> byId = new LinkedHashMap<>();
    private final Map<String, List<Closure>> byClass = new LinkedHashMap<>();

    public ClosureManifest(String graalvm, Assets assets, List<Closure> closures) {
        this.graalvm = graalvm;
        this.assets = assets;
        for (final var closure : closures) {
            byId.put(closure.id(), closure);
            byClass.computeIfAbsent(closure.implClass(), key -> new ArrayList<>()).add(closure);
        }
    }

    public boolean isEmpty() {
        return byId.isEmpty();
    }

    public String graalvm() {
        return graalvm;
    }

    /// Null for `EMPTY`.
    public Assets assets() {
        return assets;
    }

    public List<Closure> closures() {
        return List.copyOf(byId.values());
    }

    public Optional<Closure> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public boolean hasAnyClosure(Class<?> component) {
        return byClass.containsKey(component.getName());
    }

    /// Whether a closure of this class runs while the component mounts (constructor or lifecycle hook).
    public boolean hasMountClosures(Class<?> component) {
        return byClass.getOrDefault(component.getName(), List.of()).stream().anyMatch(closure -> closure.timing() == Timing.MOUNT);
    }

    /// Names of the methods of this class whose body runs a closure, for handlers that should warm the runtime.
    public Set<String> eventMethods(Class<?> component) {
        final var methods = new LinkedHashSet<String>();
        for (final var closure : byClass.getOrDefault(component.getName(), List.of())) {
            if (closure.timing() == Timing.EVENT && closure.enclosingMethod() != null) methods.add(closure.enclosingMethod());
        }
        return methods;
    }
}
