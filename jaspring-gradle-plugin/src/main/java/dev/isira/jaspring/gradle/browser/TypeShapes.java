package dev.isira.jaspring.gradle.browser;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.attribute.RecordAttribute;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.constant.ClassDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipFile;

/// Looks up the shape of application types referenced by closures (records and enums) from class files on the
/// class directories and the analysis classpath, and decides which types may cross the browser boundary.
/// The rules mirror `dev.isira.jaspring.browser.JsonShapes`.
public final class TypeShapes {

    /// A JSON-shaped type: a scalar, an enum, a record with components, an array, or a collection inside a record.
    sealed interface Shape permits Scalar, EnumShape, RecordShape, ArrayShape, ListShape, MapShape, OptionalShape {
        String sourceName();
    }

    record Scalar(String sourceName) implements Shape {}

    record EnumShape(String sourceName) implements Shape {}

    record RecordShape(String sourceName, List<Component> components) implements Shape {}

    record Component(String name, Shape shape) {}

    record ArrayShape(String sourceName, Shape element) implements Shape {}

    record ListShape(String sourceName, Shape element) implements Shape {}

    record MapShape(String sourceName, Shape value) implements Shape {}

    record OptionalShape(String sourceName, Shape value) implements Shape {}

    static final Set<String> SCALARS = Set.of("boolean", "byte", "short", "int", "long", "float", "double", "char",
            "java.lang.Boolean", "java.lang.Byte", "java.lang.Short", "java.lang.Integer", "java.lang.Long", "java.lang.Float",
            "java.lang.Double", "java.lang.Character", "java.lang.String", "java.lang.Void", "void");

    private final List<Path> classDirs;
    private final List<Path> classpathDirs;
    private final List<Path> jars;
    private final Map<String, Optional<ClassModel>> cache = new LinkedHashMap<>();

    /// `classDirs` hold the application's own classes (eligible for rewriting); `classpath` entries are jars or,
    /// for sibling projects in the same build, class directories.
    public TypeShapes(Collection<Path> classDirs, Collection<Path> classpath) {
        this.classDirs = List.copyOf(classDirs);
        this.classpathDirs = classpath.stream().filter(Files::isDirectory).toList();
        this.jars = classpath.stream().filter(path -> path.toString().endsWith(".jar") && Files.isRegularFile(path)).toList();
    }

    /// The shape of `type` at the top level of a closure boundary, or an explanation why it may not cross.
    Shape shape(ClassDesc type, boolean topLevel) {
        return shape(type, null, topLevel);
    }

    private Shape shape(ClassDesc type, String genericSignature, boolean topLevel) {
        final var name = TypeNames.source(type);
        if (type.isArray()) return new ArrayShape(name, shape(type.componentType(), componentSignature(genericSignature), topLevel));
        if (type.isPrimitive() || SCALARS.contains(name)) return new Scalar(name);
        final var binary = TypeNames.binary(type);
        if (binary.equals("java.util.List") || binary.equals("java.util.Map") || binary.equals("java.util.Optional")) {
            if (topLevel) {
                throw new IllegalArgumentException(binary.substring(binary.lastIndexOf('.') + 1)
                        + " at the top level loses its type argument to erasure and cannot cross the browser boundary; wrap it in a record");
            }
            final var arguments = typeArguments(genericSignature);
            if (binary.equals("java.util.List")) return new ListShape(name, shape(arguments.get(0).type(), arguments.get(0).signature(), false));
            if (binary.equals("java.util.Optional")) return new OptionalShape(name, shape(arguments.get(0).type(), arguments.get(0).signature(), false));
            if (!TypeNames.binary(arguments.get(0).type()).equals("java.lang.String")) throw new IllegalArgumentException("Map keys must be String in " + name);
            return new MapShape(name, shape(arguments.get(1).type(), arguments.get(1).signature(), false));
        }
        final var model = load(binary).orElseThrow(() -> new IllegalArgumentException(binary
                + " cannot cross the browser boundary; use a record, enum, String, number, boolean or array"));
        if (isEnum(model)) return new EnumShape(name);
        if (!isRecord(model)) {
            throw new IllegalArgumentException(binary + " cannot cross the browser boundary; use a record, enum, String, number, boolean or array");
        }
        final var components = new ArrayList<Component>();
        final var record = model.findAttribute(java.lang.classfile.Attributes.record()).orElseThrow();
        for (final var component : record.components()) {
            final var signature = component.findAttribute(java.lang.classfile.Attributes.signature()).map(SignatureAttribute::signature)
                    .map(entry -> entry.stringValue()).orElse(null);
            try {
                components.add(new Component(component.name().stringValue(), shape(component.descriptorSymbol(), signature, false)));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(name + "." + component.name().stringValue() + ": " + e.getMessage());
            }
        }
        return new RecordShape(name, List.copyOf(components));
    }

    private static boolean isRecord(ClassModel model) {
        return model.superclass().map(entry -> entry.asInternalName().equals("java/lang/Record")).orElse(false)
                && model.findAttribute(java.lang.classfile.Attributes.record()).isPresent();
    }

    private static boolean isEnum(ClassModel model) {
        return (model.flags().flagsMask() & ClassFile.ACC_ENUM) != 0;
    }

    Optional<ClassModel> load(String binaryName) {
        return cache.computeIfAbsent(binaryName, name -> {
            final var relative = TypeNames.internal(name) + ".class";
            try {
                for (final var dir : classDirs) {
                    final var file = dir.resolve(relative);
                    if (Files.isRegularFile(file)) return Optional.of(ClassFile.of().parse(Files.readAllBytes(file)));
                }
                for (final var dir : classpathDirs) {
                    final var file = dir.resolve(relative);
                    if (Files.isRegularFile(file)) return Optional.of(ClassFile.of().parse(Files.readAllBytes(file)));
                }
                for (final var jar : jars) {
                    try (var zip = new ZipFile(jar.toFile())) {
                        final var entry = zip.getEntry(relative);
                        if (entry != null) {
                            try (var in = zip.getInputStream(entry)) {
                                return Optional.of(ClassFile.of().parse(in.readAllBytes()));
                            }
                        }
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read " + relative, e);
            }
            return Optional.empty();
        });
    }

    /// The class directory file a type lives in, if it is application code.
    Optional<Path> classFile(String binaryName) {
        final var relative = TypeNames.internal(binaryName) + ".class";
        return classDirs.stream().map(dir -> dir.resolve(relative)).filter(Files::isRegularFile).findFirst();
    }

    // ---- generic signatures: just enough of JVMS 4.7.9.1 for List<T>, Map<String, T>, Optional<T> and arrays ----

    private record TypeArgument(ClassDesc type, String signature) {}

    private static List<TypeArgument> typeArguments(String signature) {
        if (signature == null || signature.indexOf('<') < 0) {
            throw new IllegalArgumentException("a raw collection type cannot cross the browser boundary; declare its type argument");
        }
        final var inner = signature.substring(signature.indexOf('<') + 1, signature.lastIndexOf('>'));
        final var arguments = new ArrayList<TypeArgument>();
        var index = 0;
        while (index < inner.length()) {
            final var end = endOfType(inner, index);
            final var argument = inner.substring(index, end);
            arguments.add(new TypeArgument(erase(argument), argument));
            index = end;
        }
        return arguments;
    }

    private static String componentSignature(String signature) {
        return signature != null && signature.startsWith("[") ? signature.substring(1) : null;
    }

    private static int endOfType(String text, int start) {
        var index = start;
        while (text.charAt(index) == '[') index++;
        if (text.charAt(index) != 'L') return index + 1;
        var depth = 0;
        while (index < text.length()) {
            final var c = text.charAt(index);
            if (c == '<') depth++;
            else if (c == '>') depth--;
            else if (c == ';' && depth == 0) return index + 1;
            index++;
        }
        return index;
    }

    /// Strips type arguments from a signature, leaving a descriptor.
    private static ClassDesc erase(String signature) {
        final var out = new StringBuilder();
        var depth = 0;
        for (var i = 0; i < signature.length(); i++) {
            final var c = signature.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') depth--;
            else if (depth == 0) out.append(c);
        }
        return ClassDesc.ofDescriptor(out.toString());
    }
}
