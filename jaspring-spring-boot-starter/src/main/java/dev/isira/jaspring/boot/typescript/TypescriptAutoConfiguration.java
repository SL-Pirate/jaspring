package dev.isira.jaspring.boot.typescript;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.support.ResourcePatternResolver;

import dev.isira.jaspring.boot.JaspringAutoConfiguration;
import dev.isira.jaspring.boot.JaspringProperties;

/// With `jaspring-typescript` on the classpath, compiles `src/main/typescript` at startup when the build's manifest
/// is missing or stale (an IDE run without Gradle, or a build without the plugin) and serves the result from
/// memory. Runs before the main auto-configuration so its `TypescriptScripts` bean replaces the manifest-only one.
@AutoConfiguration(before = JaspringAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "dev.isira.jaspring.typescript.TypescriptCompiler")
@ConditionalOnProperty(prefix = "jaspring.typescript", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(JaspringProperties.class)
public class TypescriptAutoConfiguration {

    @Bean
    public TypescriptScripts typescriptScripts(JaspringProperties properties, ResourcePatternResolver resolver) {
        final var settings = properties.getTypescript();
        final var manifest = TypescriptManifestLoader.load(resolver);
        final var sources = TypescriptSources.find(settings.getSourceDir(), resolver);
        return TypescriptStartupCompiler.compileIfStale(settings, manifest, sources);
    }
}
