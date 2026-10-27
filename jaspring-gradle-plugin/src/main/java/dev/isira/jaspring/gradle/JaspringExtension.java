package dev.isira.jaspring.gradle;

import org.gradle.api.Action;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;

/// `jaspring { browser { ... } }` in a consumer build. Conventions come from Gradle properties and the environment,
/// so CI can steer the WebAssembly build without editing the build script.
public abstract class JaspringExtension {

    @Nested
    public abstract Browser getBrowser();

    public void browser(Action<? super Browser> action) {
        action.execute(getBrowser());
    }

    public abstract static class Browser {

        /// Skip the WebAssembly build even when closures exist (`-Pjaspring.browser.skip=true`). `Platform.onBrowser`
        /// then fails at runtime with an explanation.
        public abstract Property<Boolean> getSkip();

        /// Oracle GraalVM home; default `GRAALVM_HOME`, else the JDK running Gradle when it is GraalVM.
        public abstract DirectoryProperty getGraalvmHome();

        /// Binaryen's `wasm-as`; default: the first one on the PATH with version 119 or later.
        public abstract RegularFileProperty getAssemblerPath();

        /// Extra `native-image` arguments, for example `-H:+ReportImageSizeBreakdown`.
        public abstract ListProperty<String> getNativeImageArgs();

        /// Extra jars the closures call into. By default only the application's classes, Jaspring's own jars and
        /// the browser runtime are compiled: library jars (Spring, GraalJS, Truffle) carry Native Image metadata
        /// that the WebAssembly backend cannot honour, and closures cannot use them in the browser anyway.
        public abstract org.gradle.api.file.ConfigurableFileCollection getImageClasspath();
    }
}
