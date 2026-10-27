package dev.isira.jaspring.gradle.browser;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.MethodHandleEntry;
import java.lang.classfile.constantpool.MethodTypeEntry;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandleInfo;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/// Finds `BrowserTask` lambdas in class files: `invokedynamic` sites bootstrapped by `LambdaMetafactory` whose
/// functional interface is `BrowserTask`. Serializable lambdas (which `BrowserTask` lambdas always are under
/// javac) use `altMetafactory`, and their synthetic methods are named `lambda$<method>$<hash>$<n>`.
public final class ClosureScanner {

    static final String BROWSER_TASK = "Ldev/isira/jaspring/browser/BrowserTask;";
    static final String BROWSER_PROMISE = "Ldev/isira/jaspring/browser/BrowserPromise;";
    private static final Set<String> MOUNT_METHODS = Set.of("<init>", "<clinit>", "new", "ngOnInit", "ngOnChanges", "ngDoCheck",
            "ngAfterContentInit", "ngAfterContentChecked", "ngAfterViewInit", "ngAfterViewChecked");
    private static final Pattern LAMBDA_NAME = Pattern.compile("^lambda\\$([^$]+)\\$.*");

    private ClosureScanner() {
    }

    public static List<FoundClosure> scan(Collection<Path> classDirs, Collection<Path> analysisClasspath) throws IOException {
        final var shapes = new TypeShapes(classDirs, analysisClasspath);
        final var found = new LinkedHashMap<String, FoundClosure>();
        for (final var dir : classDirs) {
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> files = Files.walk(dir)) {
                for (final var file : files.filter(path -> path.toString().endsWith(".class")).sorted().toList()) {
                    final var bytes = Files.readAllBytes(file);
                    scanClass(ClassFile.of().parse(bytes), sha256(bytes), shapes, found);
                }
            }
        }
        return List.copyOf(found.values());
    }

    private static void scanClass(ClassModel model, String digest, TypeShapes shapes, LinkedHashMap<String, FoundClosure> found) {
        for (final var method : model.methods()) {
            // javac's $deserializeLambda$ repeats every serializable lambda site; the real sites carry the context.
            if (method.methodName().equalsString("$deserializeLambda$")) continue;
            final var code = method.code();
            if (code.isEmpty()) continue;
            ClassDesc lastClassConstant = null;
            for (final var element : code.get()) {
                if (element instanceof ConstantInstruction.LoadConstantInstruction constant
                        && constant.constantEntry() instanceof ClassEntry classEntry) {
                    lastClassConstant = classEntry.asSymbol();
                }
                if (element instanceof InvokeDynamicInstruction indy && indy.typeSymbol().returnType().descriptorString().equals(BROWSER_TASK)) {
                    final var closure = closure(model, method, indy, lastClassConstant, digest, shapes);
                    found.putIfAbsent(closure.id(), closure);
                }
            }
        }
    }

    private static FoundClosure closure(ClassModel model, MethodModel method, InvokeDynamicInstruction indy, ClassDesc lastClassConstant,
            String digest, TypeShapes shapes) {
        final var owner = model.thisClass().asInternalName().replace('/', '.');
        final var site = owner + "." + enclosingName(method);
        final var bootstrap = indy.invokedynamic().bootstrap();
        final var factory = bootstrap.bootstrapMethod().reference();
        if (!factory.owner().asInternalName().equals("java/lang/invoke/LambdaMetafactory")) {
            throw new IllegalArgumentException(site + ": BrowserTask lambdas must be plain Java lambdas or method references");
        }
        if (!factory.name().equalsString("altMetafactory")) {
            throw new IllegalArgumentException(site + ": the BrowserTask lambda is not serializable; compile with javac");
        }
        final var arguments = bootstrap.arguments();
        final var impl = (MethodHandleEntry) arguments.get(1);
        final var instantiated = (MethodTypeEntry) arguments.get(2);
        if (impl.kind() != MethodHandleInfo.REF_invokeStatic) {
            throw new IllegalArgumentException(site + ": the browser closure uses instance members of the component (this);"
                    + " copy what it needs into local variables first");
        }
        final var implClass = impl.reference().owner().asInternalName().replace('/', '.');
        final var implName = impl.reference().name().stringValue();
        final var implDescriptor = impl.reference().type().stringValue();
        final var id = ClosureIds.of(implClass, implName, implDescriptor);

        final var captured = new ArrayList<TypeShapes.Shape>();
        for (final var type : indy.typeSymbol().parameterList()) {
            captured.add(validate(site, "captures a " + TypeNames.source(type) + " that", type, shapes));
        }
        final var returnType = instantiated.asSymbol().returnType();
        final var promise = returnType.descriptorString().equals(BROWSER_PROMISE);
        final TypeShapes.Shape returnShape;
        if (promise) {
            if (lastClassConstant == null) {
                throw new IllegalArgumentException(site + ": Platform.onBrowserAsync needs the result type as a class literal, e.g. onBrowserAsync(Position.class, ...)");
            }
            returnShape = validate(site, "resolves to a " + TypeNames.source(lastClassConstant) + " that", lastClassConstant, shapes);
        } else {
            returnShape = validate(site, "returns a " + TypeNames.source(returnType) + " that", returnType, shapes);
        }
        final var enclosing = enclosingName(method);
        final var timing = MOUNT_METHODS.contains(enclosing) ? FoundClosure.Timing.MOUNT : FoundClosure.Timing.EVENT;
        return new FoundClosure(id, implClass, implName, implDescriptor, List.copyOf(captured), returnShape, promise, timing, enclosing, digest);
    }

    private static TypeShapes.Shape validate(String site, String what, ClassDesc type, TypeShapes shapes) {
        try {
            return shapes.shape(type, true);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(site + " " + what + " " + e.getMessage(), e);
        }
    }

    /// The source method a lambda site belongs to: nested lambdas report the method they were written in.
    static String enclosingName(MethodModel method) {
        final var name = method.methodName().stringValue();
        final var matcher = LAMBDA_NAME.matcher(name);
        return matcher.matches() ? matcher.group(1) : name;
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /// Same formula as `dev.isira.jaspring.browser.ClosureIds` in core; the plugin has no dependency on core.
    static final class ClosureIds {
        static String of(String implClass, String implMethodName, String implMethodSignature) {
            final var text = implClass.replace('.', '/') + "#" + implMethodName + implMethodSignature;
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)), 0, 8);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }

        private ClosureIds() {
        }
    }

    static MethodTypeDesc descriptor(String descriptor) {
        return MethodTypeDesc.ofDescriptor(descriptor);
    }
}
