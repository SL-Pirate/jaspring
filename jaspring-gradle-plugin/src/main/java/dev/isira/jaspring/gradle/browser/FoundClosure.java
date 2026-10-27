package dev.isira.jaspring.gradle.browser;

import java.util.List;

/// One `Platform.onBrowser` lambda found in the compiled classes. Names are source-like (`com.example.Spot`,
/// `int[]`); `implSignature` is the JVM descriptor of the synthetic lambda method.
public record FoundClosure(String id, String implClass, String implMethod, String implSignature, List<TypeShapes.Shape> captured,
        TypeShapes.Shape returnShape, boolean promise, Timing timing, String enclosingMethod, String classSha256) {

    public enum Timing { MOUNT, EVENT }

    public String returnType() {
        return returnShape.sourceName();
    }
}
