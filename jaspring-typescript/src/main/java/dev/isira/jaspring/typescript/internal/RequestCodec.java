package dev.isira.jaspring.typescript.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.isira.jaspring.typescript.CompileOptions;
import dev.isira.jaspring.typescript.CompileRequest;
import dev.isira.jaspring.typescript.CompileResult;
import dev.isira.jaspring.typescript.SourceFile;
import dev.isira.jaspring.typescript.TypescriptException;

/// The wire format of `run(requestJson)` in `tools/js-bundle/src/typescript.mjs`, the same on both hosts.
public final class RequestCodec {

    private RequestCodec() {
    }

    public static String version() {
        return "{\"op\":\"version\"}";
    }

    public static String options() {
        return "{\"op\":\"options\"}";
    }

    public static String compile(List<SourceFile> files, List<SourceFile> types, CompileOptions options) {
        return compile(new CompileRequest(files, options), types, false);
    }

    public static String compile(CompileRequest input, List<SourceFile> types, boolean noEmit) {
        final var request = new LinkedHashMap<String, Object>();
        request.put("op", "compile");
        final var options = options(input.options());
        options.put("noEmit", noEmit);
        request.put("options", options);
        request.put("types", files(types));
        request.put("files", files(input.files()));
        request.put("dependencies", Map.of("files", input.dependencies().files(), "globalTypes", input.dependencies().globalTypes(),
                "allowedImports", input.dependencies().allowedImports()));
        return Json.write(request);
    }

    public static String transpile(SourceFile file, CompileOptions options) {
        final var request = new LinkedHashMap<String, Object>();
        request.put("op", "transpile");
        request.put("options", options(options));
        request.put("file", file(file));
        return Json.write(request);
    }

    private static Map<String, Object> options(CompileOptions options) {
        final var map = new LinkedHashMap<String, Object>();
        map.put("typeCheck", options.typeCheck());
        map.put("sourceMap", options.sourceMap());
        return map;
    }

    private static List<Object> files(List<SourceFile> files) {
        final var list = new ArrayList<Object>(files.size());
        for (final var file : files) list.add(file(file));
        return list;
    }

    private static Map<String, Object> file(SourceFile file) {
        final var map = new LinkedHashMap<String, Object>();
        map.put("path", file.path());
        map.put("text", file.text());
        return map;
    }

    /// Any response as an object; a bundle crash (`{"error": ...}`) becomes a [TypescriptException].
    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(String json) {
        final Map<String, Object> root;
        try {
            root = (Map<String, Object>) Json.parse(json);
        } catch (RuntimeException e) {
            throw new TypescriptException("The compiler returned malformed JSON: " + abbreviate(json), e);
        }
        if (root.get("error") instanceof Map<?, ?> error) {
            final var stack = error.get("stack");
            throw new TypescriptException("The TypeScript compiler failed: " + error.get("message") + (stack == null ? "" : "\n" + stack));
        }
        return root;
    }

    @SuppressWarnings("unchecked")
    public static CompileResult result(String json) {
        final var root = object(json);
        final var outputs = new ArrayList<CompileResult.Output>();
        for (final var entry : (List<Map<String, Object>>) root.getOrDefault("outputs", List.of())) {
            outputs.add(new CompileResult.Output((String) entry.get("path"), (String) entry.get("text")));
        }
        final var diagnostics = new ArrayList<CompileResult.Diagnostic>();
        for (final var entry : (List<Map<String, Object>>) root.getOrDefault("diagnostics", List.of())) {
            diagnostics.add(new CompileResult.Diagnostic((String) entry.get("file"), number(entry.get("line")), number(entry.get("column")),
                    number(entry.get("code")), CompileResult.Diagnostic.Category.valueOf(((String) entry.get("category")).toUpperCase(Locale.ROOT)),
                    (String) entry.get("message")));
        }
        return new CompileResult(outputs, diagnostics);
    }

    private static int number(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    private static String abbreviate(String text) {
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }
}
