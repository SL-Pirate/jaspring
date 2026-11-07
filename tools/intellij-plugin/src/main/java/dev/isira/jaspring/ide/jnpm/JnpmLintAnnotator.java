package dev.isira.jaspring.ide.jnpm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonParser;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/// IntelliJ schedules external annotators after editing settles. Linting runs off the UI thread against the
/// captured, possibly unsaved document; stale results and fixes are discarded using the document stamp.
public final class JnpmLintAnnotator extends ExternalAnnotator<JnpmLintAnnotator.Input, JnpmLintAnnotator.Result> {
    record Input(String file, String text, long stamp, JnpmProjectService.Module module) {}
    record Problem(int start, int end, String message, boolean error, Fix fix) {}
    record Fix(int start, int end, String text) {}
    record Result(long stamp, List<Problem> problems) {}

    @Override
    public Input collectInformation(@NotNull PsiFile file) {
        if (!com.intellij.ide.trustedProjects.TrustedProjects.isProjectTrusted(file.getProject())) return null;
        final var virtual = file.getVirtualFile();
        if (virtual == null || virtual.getName().endsWith(".d.ts")) return null;
        final var module = JnpmProjectService.get(file.getProject()).forFile(virtual.getPath());
        if (module == null || !module.lint) return null;
        final var document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        return document == null ? null : new Input(virtual.getPath(), document.getText(), document.getModificationStamp(), module);
    }

    @Override
    public Result doAnnotate(Input input) {
        if (!Files.isRegularFile(input.module.metadata())) return problem(input, "JNPM dependencies are missing. Use Tools → JNPM → Prepare Dependencies.");
        CapturingProcessHandler handler = null;
        try {
            final var metadata = JsonParser.parseString(Files.readString(input.module.metadata())).getAsJsonObject();
            final var command = new GeneralCommandLine(metadata.get("node").getAsString(), metadata.get("bridge").getAsString(),
                    "lint-server", metadata.get("config").getAsString()).withWorkDirectory(input.module.project).withCharset(StandardCharsets.UTF_8);
            handler = new CapturingProcessHandler(command);
            final var request = new com.google.gson.JsonObject();
            request.addProperty("id", input.stamp);
            request.addProperty("file", input.file);
            request.addProperty("text", input.text);
            final var process = handler.getProcess();
            // A large document must not block cancellation if a custom ESLint config hangs before reading stdin.
            Thread.ofVirtual().name("jnpm-eslint-input").start(() -> {
                try (var output = process.getOutputStream()) {
                    output.write((request + "\n").getBytes(StandardCharsets.UTF_8));
                } catch (java.io.IOException ignored) {
                    // The process handler reports exits, timeouts and cancellation below.
                }
            });
            final var indicator = com.intellij.openapi.progress.ProgressManager.getInstance().getProgressIndicator();
            final var output = indicator == null ? handler.runProcess(15_000, true)
                    : handler.runProcessWithProgressIndicator(indicator, 15_000, true);
            if (output.isCancelled()) throw new ProcessCanceledException();
            if (output.isTimeout()) return problem(input, "JNPM ESLint timed out; check the project's ESLint configuration.");
            if (output.getExitCode() != 0) return problem(input, "JNPM ESLint failed: " + output.getStderr().strip());
            final var response = output.getStdout().lines().filter(line -> line.startsWith("{")).reduce((first, second) -> second).orElse("{}");
            final var json = JsonParser.parseString(response).getAsJsonObject();
            if (json.has("error")) return problem(input, "JNPM ESLint: " + json.get("error").getAsString());
            final var problems = new ArrayList<Problem>();
            if (json.has("messages")) for (final var element : json.getAsJsonArray("messages")) {
                final var message = element.getAsJsonObject();
                final var start = offset(input.text, integer(message, "line", 1), integer(message, "column", 1));
                final var end = Math.max(start, offset(input.text, integer(message, "endLine", integer(message, "line", 1)), integer(message, "endColumn", integer(message, "column", 1) + 1)));
                Fix fix = null;
                if (message.has("fix")) {
                    final var edit = message.getAsJsonObject("fix");
                    fix = new Fix(edit.getAsJsonArray("range").get(0).getAsInt(), edit.getAsJsonArray("range").get(1).getAsInt(), edit.get("text").getAsString());
                }
                final var rule = message.has("ruleId") && !message.get("ruleId").isJsonNull() ? " [" + message.get("ruleId").getAsString() + "]" : "";
                problems.add(new Problem(start, end, message.get("message").getAsString() + rule, integer(message, "severity", 1) == 2, fix));
            }
            return new Result(input.stamp, List.copyOf(problems));
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Exception e) {
            return problem(input, "JNPM ESLint unavailable: " + e.getMessage());
        } finally {
            if (handler != null && !handler.isProcessTerminated()) handler.destroyProcess();
        }
    }

    @Override
    public void apply(@NotNull PsiFile file, Result result, @NotNull AnnotationHolder holder) {
        final var document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (document == null || document.getModificationStamp() != result.stamp) return;
        for (final var problem : result.problems) {
            final var annotation = holder.newAnnotation(problem.error ? HighlightSeverity.ERROR : HighlightSeverity.WARNING, problem.message)
                    .range(new TextRange(problem.start, problem.end));
            if (problem.fix != null) annotation.withFix(new ApplyFix(problem.fix, result.stamp));
            annotation.create();
        }
    }

    static int offset(String text, int line, int column) {
        var offset = 0;
        for (var current = 1; current < line && offset < text.length(); current++) {
            final var newline = text.indexOf('\n', offset);
            offset = newline < 0 ? text.length() : newline + 1;
        }
        final var end = text.indexOf('\n', offset);
        return Math.min(end < 0 ? text.length() : end, offset + Math.max(0, column - 1));
    }

    private static int integer(com.google.gson.JsonObject object, String key, int fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsInt() : fallback;
    }

    private static Result problem(Input input, String message) {
        return new Result(input.stamp, List.of(new Problem(0, Math.min(1, input.text.length()), message, false, null)));
    }

    private record ApplyFix(Fix fix, long stamp) implements IntentionAction {
        @Override public @NotNull String getText() { return "Apply ESLint fix"; }
        @Override public @NotNull String getFamilyName() { return "JNPM ESLint"; }
        @Override public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
            return editor != null && editor.getDocument().getModificationStamp() == stamp
                    && fix.start >= 0 && fix.end >= fix.start && fix.end <= editor.getDocument().getTextLength();
        }
        @Override public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
            if (isAvailable(project, editor, file)) editor.getDocument().replaceString(fix.start, fix.end, fix.text);
        }
        @Override public boolean startInWriteAction() { return true; }
    }
}
