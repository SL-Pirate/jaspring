package dev.isira.jaspring.gradle.browser;

import java.io.IOException;
import java.lang.classfile.AccessFlags;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/// Prepares copies of the classes that own closures for the Web Image build: the synthetic lambda methods become
/// public static so the generated dispatcher can call them, and everything that would drag the server runtime
/// into the image is dropped (instance members, constructors, annotations). Static fields and initialisers stay,
/// so static state is reachable from closures and documented as a hazard.
public final class ClosureRewriter {

    private ClosureRewriter() {
    }

    /// Writes the rewritten owners of `closures` (and copies referenced records and enums unchanged) under `target`.
    public static void rewrite(List<FoundClosure> closures, TypeShapes shapes, Path target) throws IOException {
        final var owners = new LinkedHashSet<String>();
        for (final var closure : closures) {
            owners.add(closure.implClass());
        }
        for (final var owner : owners) {
            final var source = shapes.classFile(owner).orElseThrow(() -> new IllegalStateException(owner + " is not in the class directories"));
            write(target, owner, rewrite(Files.readAllBytes(source)));
        }
        for (final var type : referencedTypes(closures)) {
            if (owners.contains(type)) continue;
            final var source = shapes.classFile(type);
            if (source.isPresent()) write(target, type, Files.readAllBytes(source.get()));
        }
    }

    static byte[] rewrite(byte[] original) {
        final var classFile = ClassFile.of();
        final var model = classFile.parse(original);
        final ClassTransform transform = (builder, element) -> {
            switch (element) {
                case AccessFlags flags -> builder.withFlags((flags.flagsMask() & ~(ClassFile.ACC_PRIVATE | ClassFile.ACC_PROTECTED)) | ClassFile.ACC_PUBLIC);
                case MethodModel method -> {
                    final var flags = method.flags().flagsMask();
                    final var isStatic = (flags & ClassFile.ACC_STATIC) != 0;
                    if (!isStatic || method.methodName().equalsString("<init>")) return;
                    builder.transformMethod(method, widen());
                }
                case FieldModel field -> {
                    if ((field.flags().flagsMask() & ClassFile.ACC_STATIC) == 0) return;
                    builder.transformField(field, (fieldBuilder, fieldElement) -> {
                        if (fieldElement instanceof AccessFlags flags) {
                            fieldBuilder.withFlags((flags.flagsMask() & ~(ClassFile.ACC_PRIVATE | ClassFile.ACC_PROTECTED)) | ClassFile.ACC_PUBLIC);
                        } else if (!(fieldElement instanceof RuntimeVisibleAnnotationsAttribute) && !(fieldElement instanceof RuntimeInvisibleAnnotationsAttribute)) {
                            fieldBuilder.with(fieldElement);
                        }
                    });
                }
                case RuntimeVisibleAnnotationsAttribute ignored -> { }
                case RuntimeInvisibleAnnotationsAttribute ignored -> { }
                default -> builder.with(element);
            }
        };
        final var rewritten = classFile.transformClass(model, transform);
        classFile.parse(rewritten); // fail here, not inside native-image, if the transform produced garbage
        return rewritten;
    }

    /// Public, and no longer synthetic: javac does not resolve synthetic members from source, and the generated
    /// dispatcher must call the lambda method by name.
    private static MethodTransform widen() {
        return (methodBuilder, methodElement) -> {
            if (methodElement instanceof AccessFlags flags) {
                methodBuilder.withFlags((flags.flagsMask() & ~(ClassFile.ACC_PRIVATE | ClassFile.ACC_PROTECTED | ClassFile.ACC_SYNTHETIC | ClassFile.ACC_BRIDGE))
                        | ClassFile.ACC_PUBLIC);
            } else if (!(methodElement instanceof RuntimeVisibleAnnotationsAttribute) && !(methodElement instanceof RuntimeInvisibleAnnotationsAttribute)) {
                methodBuilder.with(methodElement);
            }
        };
    }

    /// Application records and enums the closures' captured values and results are made of.
    static Collection<String> referencedTypes(List<FoundClosure> closures) {
        final var types = new LinkedHashSet<String>();
        for (final var closure : closures) {
            closure.captured().forEach(shape -> collect(shape, types));
            collect(closure.returnShape(), types);
        }
        return types;
    }

    private static void collect(TypeShapes.Shape shape, LinkedHashSet<String> types) {
        switch (shape) {
            case TypeShapes.RecordShape record -> {
                types.add(record.sourceName());
                record.components().forEach(component -> collect(component.shape(), types));
            }
            case TypeShapes.EnumShape enumShape -> types.add(enumShape.sourceName());
            case TypeShapes.ArrayShape array -> collect(array.element(), types);
            case TypeShapes.ListShape list -> collect(list.element(), types);
            case TypeShapes.MapShape map -> collect(map.value(), types);
            case TypeShapes.OptionalShape optional -> collect(optional.value(), types);
            case TypeShapes.Scalar ignored -> { }
        }
    }

    private static void write(Path target, String binaryName, byte[] bytes) throws IOException {
        final var file = target.resolve(TypeNames.internal(binaryName) + ".class");
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }
}
