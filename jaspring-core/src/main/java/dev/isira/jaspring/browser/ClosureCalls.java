package dev.isira.jaspring.browser;

import java.io.IOException;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Type;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.isira.jaspring.live.LivePage;
import dev.isira.jaspring.view.ComponentView;
import dev.isira.jaspring.zone.ZoneTask;

/// Turns a `BrowserTask` lambda into a `wasm` browser call: identifies the compiled closure through the lambda's
/// `SerializedLambda`, ships the captured values, and converts the JSON answer to the closure's return type.
final class ClosureCalls {

    private static final String PROMISE_DESCRIPTOR = ")L" + BrowserPromise.class.getName().replace('.', '/') + ";";
    private static final Map<String, String> CLASS_DIGESTS = new ConcurrentHashMap<>();

    private ClosureCalls() {
    }

    static <R> ZoneTask<R> invoke(LivePage page, ComponentView view, Object task, Class<R> promiseType) {
        if (view == null) {
            throw new IllegalStateException("Platform.onBrowser needs a component context: call it in a constructor, a lifecycle hook or an event handler");
        }
        final var lambda = serialized(task);
        final var name = describe(lambda);
        if (lambda.getImplMethodKind() != MethodHandleInfo.REF_invokeStatic) {
            throw new IllegalArgumentException(name + " uses instance members of the component (this); copy what it needs into local variables first");
        }
        final var returnsPromise = lambda.getInstantiatedMethodType().endsWith(PROMISE_DESCRIPTOR);
        if (returnsPromise && promiseType == null) {
            throw new IllegalArgumentException(name + " returns a BrowserPromise; use Platform.onBrowserAsync(type, task)");
        }
        if (!returnsPromise && promiseType != null) {
            throw new IllegalArgumentException(name + " does not return a BrowserPromise; use Platform.onBrowser(task)");
        }
        final var captured = new ArrayList<Object>(lambda.getCapturedArgCount());
        for (var i = 0; i < lambda.getCapturedArgCount(); i++) {
            final var value = lambda.getCapturedArg(i);
            if (value != null) {
                JsonShapes.rejection(value.getClass(), true).ifPresent(reason -> {
                    throw new IllegalArgumentException(name + " captures a value that " + reason);
                });
            }
            captured.add(value);
        }

        final var manifest = page.closures();
        if (manifest.isEmpty()) {
            throw new BrowserClosuresUnavailableException("No jaspring/jaspring-browser.json on the classpath: apply the dev.isira.jaspring Gradle plugin"
                    + " and build with Oracle GraalVM, or the build ran with jaspring.browser.skip");
        }
        final var id = ClosureIds.of(lambda.getImplClass(), lambda.getImplMethodName(), lambda.getImplMethodSignature());
        final var closure = manifest.find(id).orElseThrow(() -> new BrowserClosuresUnavailableException("Stale WebAssembly build: " + name
                + " is not in jaspring-browser.json; rebuild with Gradle (IDE and devtools builds do not rebuild the image)"));
        final var loader = task.getClass().getClassLoader();
        final var digest = classDigest(closure.implClass(), loader);
        if (closure.classSha256() != null && digest != null && !closure.classSha256().equalsIgnoreCase(digest)) {
            throw new BrowserClosuresUnavailableException("Stale WebAssembly build: " + closure.implClass()
                    + " changed since the image was built; rebuild with Gradle");
        }
        final Type returnType = promiseType != null ? promiseType : resolveType(closure.returnType(), loader);

        final ZoneTask<Object> answer = page.callBrowser("wasm", view.id(), List.of(id, view.id(), captured));
        @SuppressWarnings("unchecked")
        final var converted = (ZoneTask<R>) answer.thenApply(value -> JsonShapes.<Object>convert(value, returnType));
        return converted;
    }

    static SerializedLambda serialized(Object task) {
        try {
            final var writeReplace = task.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            if (writeReplace.invoke(task) instanceof SerializedLambda lambda) return lambda;
        } catch (NoSuchMethodException e) {
            // Not a lambda; reported below.
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("Cannot inspect the browser task " + task.getClass().getName(), e);
        }
        throw new IllegalArgumentException("Platform.onBrowser needs a lambda or a static method reference, not " + task.getClass().getName());
    }

    private static String describe(SerializedLambda lambda) {
        return lambda.getImplClass().replace('/', '.') + "." + lambda.getImplMethodName();
    }

    /// SHA-256 of the class file on the classpath, or null when it cannot be read.
    private static String classDigest(String className, ClassLoader loader) {
        return CLASS_DIGESTS.computeIfAbsent(className, name -> {
            final var resource = name.replace('.', '/') + ".class";
            try (var in = loader == null ? ClassLoader.getSystemResourceAsStream(resource) : loader.getResourceAsStream(resource)) {
                if (in == null) return "";
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
            } catch (IOException | NoSuchAlgorithmException e) {
                return "";
            }
        }).isEmpty() ? null : CLASS_DIGESTS.get(className);
    }

    /// Source-like type names as the manifest records them: `int`, `java.lang.String`, `com.example.Spot`, `int[]`.
    static Type resolveType(String name, ClassLoader loader) {
        if (name == null || name.equals("void")) return Void.class;
        if (name.endsWith("[]")) {
            final var component = resolveType(name.substring(0, name.length() - 2), loader);
            return java.lang.reflect.Array.newInstance((Class<?>) component, 0).getClass();
        }
        return switch (name) {
            case "boolean" -> boolean.class;
            case "byte" -> byte.class;
            case "short" -> short.class;
            case "int" -> int.class;
            case "long" -> long.class;
            case "float" -> float.class;
            case "double" -> double.class;
            case "char" -> char.class;
            default -> {
                try {
                    yield Class.forName(name, false, loader);
                } catch (ClassNotFoundException e) {
                    throw new BrowserClosuresUnavailableException("The manifest names the type " + name + ", which is not on the classpath");
                }
            }
        };
    }
}
