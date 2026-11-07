package dev.isira.jaspring.gradle.jnpm;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.gradle.api.logging.Logger;

/// The compiler task and JNPM tasks share one tool adapter. Config and toolchain files contain no credentials.
public final class JnpmBuildSupport {
    private JnpmBuildSupport() {}

    public static void run(Path config, String command, List<String> arguments, Logger logger) throws IOException {
        final var toolchain = JnpmFiles.read(config.getParent().resolve("toolchain.json"));
        final var bridge = JnpmFiles.bridge(config.getParent());
        final var invocation = new ArrayList<>(List.of(toolchain.get("node").getAsString(), bridge.toString(), command, config.toString()));
        invocation.addAll(arguments);
        JnpmProcess.run(invocation, config.getParent().getParent(), Map.of(), logger);
    }
}
