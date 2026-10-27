package dev.isira.jaspring.gradle.browser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.classfile.ClassFile;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClosureRewriterTest {

    @TempDir
    Path temp;

    @Test
    void widensLambdaMethodsDropsInstanceMembersAndAnnotationsAndStaysLoadable() throws Exception {
        final var classes = temp.resolve("classes");
        final var rewritten = temp.resolve("rewritten");
        Fixtures.compile(classes, Map.of("fx.Comp", Fixtures.COMPONENT));
        final var shapes = new TypeShapes(List.of(classes), List.of(Fixtures.CORE_JAR));
        final var closures = ClosureScanner.scan(List.of(classes), List.of(Fixtures.CORE_JAR));

        ClosureRewriter.rewrite(closures, shapes, rewritten);

        assertTrue(Files.exists(rewritten.resolve("fx/Comp$Spot.class")), "referenced record copied");
        assertTrue(Files.exists(rewritten.resolve("fx/Comp$Kind.class")), "referenced enum copied");
        final var model = ClassFile.of().parse(Files.readAllBytes(rewritten.resolve("fx/Comp.class")));
        assertTrue(model.findAttribute(java.lang.classfile.Attributes.runtimeVisibleAnnotations()).isEmpty(), "@Deprecated dropped");
        final var methods = model.methods().stream().map(m -> m.methodName().stringValue()).toList();
        assertFalse(methods.contains("<init>"), methods.toString());
        assertFalse(methods.contains("plain"), "instance methods dropped: " + methods);
        for (final var method : model.methods()) {
            final var flags = method.flags().flagsMask();
            assertTrue((flags & ClassFile.ACC_STATIC) != 0, method.methodName().stringValue() + " is static");
            assertTrue((flags & ClassFile.ACC_PUBLIC) != 0, method.methodName().stringValue() + " is public");
        }
        assertEquals(List.of("counter"), model.fields().stream().map(f -> f.fieldName().stringValue()).toList(), "only static fields remain");

        try (var loader = new URLClassLoader(new java.net.URL[] {rewritten.toUri().toURL(), Fixtures.CORE_JAR.toUri().toURL()}, null)) {
            final var comp = loader.loadClass("fx.Comp");
            final var save = closures.stream().filter(c -> c.enclosingMethod().equals("save")).findFirst().orElseThrow();
            final var lambda = java.util.Arrays.stream(comp.getDeclaredMethods()).filter(m -> m.getName().equals(save.implMethod())).findFirst().orElseThrow();
            assertTrue(Modifier.isPublic(lambda.getModifiers()) && Modifier.isStatic(lambda.getModifiers()));
            final var clientType = loader.loadClass("dev.isira.jaspring.browser.BrowserClient");
            final var locationType = loader.loadClass("dev.isira.jaspring.browser.Location");
            final var location = Proxy.newProxyInstance(loader, new Class<?>[] {locationType}, (p, m, a) -> m.getName().equals("hash") ? "#top" : null);
            final var client = Proxy.newProxyInstance(loader, new Class<?>[] {clientType}, (p, m, a) -> m.getName().equals("location") ? location : null);
            final var spot = lambda.invoke(null, "Colombo", 3, client);
            assertTrue(spot.toString().contains("Colombo#top"), spot.toString());
        }
    }
}
