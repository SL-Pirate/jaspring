package dev.isira.jaspring.boot.typescript;

/// The startup fallback found errors in `src/main/typescript`. Startup fails, like it does for a template Angular
/// rejects; `jaspring.typescript.type-check=false` transpiles without checking.
public class TypescriptCompileException extends RuntimeException {

    public TypescriptCompileException(String message) {
        super(message);
    }
}
