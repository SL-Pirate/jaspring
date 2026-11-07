package dev.isira.jaspring.gradle.jnpm.model;

import java.io.Serializable;
import java.util.Map;

/// Serializable Gradle tooling contract; no Gradle or IntelliJ classes cross the boundary.
public interface JnpmModel extends Serializable {
    boolean getEnabled();
    String getProjectDirectory();
    String getRootDirectory();
    String getBuildDirectory();
    String getGradlePath();
    String getSourceDirectory();
    boolean getLintEnabled();
    Map<String, Map<String, String>> getScopes();
}
