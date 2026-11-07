package dev.isira.jaspring.typescript;

/// What a caller may vary. Target, module format, strictness and the lib set are fixed inside the bundle: the
/// `.js` import rule and the shipped `tsconfig.base.json` depend on them, and both hosts and both callers (the
/// Gradle task and the startup fallback) must agree.
///
/// - `typeCheck`: off means each file is transpiled alone; syntax errors are still reported, type errors are not.
/// - `sourceMap`: also emit `.js.map` files.
public record CompileOptions(boolean typeCheck, boolean sourceMap) {

    public static CompileOptions defaults() {
        return new CompileOptions(true, false);
    }

    public static CompileOptions transpileOnly() {
        return new CompileOptions(false, false);
    }
}
