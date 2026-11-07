package dev.isira.jaspring.gradle.jnpm.model;

import java.util.Map;

public record DefaultJnpmModel(boolean enabled, String projectDirectory, String rootDirectory, String buildDirectory,
                               String gradlePath, String sourceDirectory, boolean lintEnabled,
                               Map<String, Map<String, String>> scopes) implements JnpmModel {
    public DefaultJnpmModel {
        final var copy = new java.util.LinkedHashMap<String, Map<String, String>>();
        scopes.forEach((name, dependencies) -> copy.put(name, Map.copyOf(dependencies)));
        scopes = Map.copyOf(copy);
    }

    @Override public boolean getEnabled() { return enabled; }
    @Override public String getProjectDirectory() { return projectDirectory; }
    @Override public String getRootDirectory() { return rootDirectory; }
    @Override public String getBuildDirectory() { return buildDirectory; }
    @Override public String getGradlePath() { return gradlePath; }
    @Override public String getSourceDirectory() { return sourceDirectory; }
    @Override public boolean getLintEnabled() { return lintEnabled; }
    @Override public Map<String, Map<String, String>> getScopes() { return scopes; }
}
