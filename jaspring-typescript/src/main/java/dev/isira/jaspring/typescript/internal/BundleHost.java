package dev.isira.jaspring.typescript.internal;

/// Runs the bundle's `run(requestJson)` somewhere and returns its JSON string. Both implementations execute the
/// same file, so results are identical; only speed and prerequisites differ.
public sealed interface BundleHost extends AutoCloseable permits GraalBundleHost, NodeBundleHost {

    String run(String requestJson);

    /// True when GraalJS runs without the optimizing compiler (a JDK other than GraalVM, or no native access),
    /// which makes a type-checked compile several times slower.
    boolean interpreted();

    @Override
    void close();
}
