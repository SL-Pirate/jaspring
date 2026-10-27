package dev.isira.jaspring.gradle.browser;

import java.lang.constant.ClassDesc;

/// Conversions between JVM descriptors, internal names and the source-like names the manifest uses
/// (`int`, `java.lang.String`, `com.example.Spot`, `int[]`).
final class TypeNames {

    private TypeNames() {
    }

    static String source(ClassDesc type) {
        if (type.isArray()) return source(type.componentType()) + "[]";
        if (type.isPrimitive()) return type.displayName();
        return type.packageName().isEmpty() ? type.displayName().replace('$', '.') : binary(type);
    }

    /// The binary (dotted, `$`-nested) name of a class descriptor.
    static String binary(ClassDesc type) {
        final var descriptor = type.descriptorString();
        return descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
    }

    static String internal(String binaryName) {
        return binaryName.replace('.', '/');
    }

    /// A Java source expression naming the type, for generated code (`com.example.Outer.Inner`, `int[]`).
    static String javaSource(String sourceName) {
        return sourceName.replace('$', '.');
    }
}
