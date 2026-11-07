package dev.isira.jaspring.gradle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;

/// Runs the compiler's CLI inside the worker and leaves its exit code in a file; the task reads the diagnostics
/// and decides, so the message and the hint live in one place. The CLI keeps the loaded compiler in a static
/// field, and Gradle reuses worker daemons, so a second build in the same session skips the bundle load.
public abstract class CompileTypescriptAction implements WorkAction<CompileTypescriptAction.Parameters> {

    public interface Parameters extends WorkParameters {
        ListProperty<String> getArguments();

        RegularFileProperty getExitFile();
    }

    @Override
    public void execute() {
        int code;
        try {
            final var cli = Class.forName(CompileTypescript.CLI_CLASS, true, CompileTypescriptAction.class.getClassLoader());
            code = (int) cli.getMethod("run", String[].class).invoke(null, (Object) getParameters().getArguments().get().toArray(String[]::new));
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(CompileTypescript.CLI_CLASS + " is not on the " + JaspringPlugin.TYPESCRIPT_CONFIGURATION
                    + " configuration; it needs the dev.isira:jaspring-typescript jar", e);
        } catch (ReflectiveOperationException e) {
            final var cause = e instanceof InvocationTargetException invocation && invocation.getCause() != null ? invocation.getCause() : e;
            throw new IllegalStateException("The TypeScript compiler failed: " + cause, cause);
        }
        try {
            Files.writeString(getParameters().getExitFile().get().getAsFile().toPath(), Integer.toString(code));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
