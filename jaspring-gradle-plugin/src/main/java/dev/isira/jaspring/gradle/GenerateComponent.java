package dev.isira.jaspring.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.work.DisableCachingByDefault;

import javax.lang.model.SourceVersion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Locale;

/// Creates editable source files. Existing files are never overwritten, and generation is never cached.
@DisableCachingByDefault(because = "Scaffolds user-owned sources, not reproducible build outputs")
public abstract class GenerateComponent extends DefaultTask {
    @Input
    @Optional
    @Option(option = "name", description = "Component name, e.g. user-card, UserCard or UserCardComponent")
    public abstract Property<String> getComponentName();

    @Input
    @Optional
    @Option(option = "package", description = "Java package, e.g. com.example.components")
    public abstract Property<String> getPackageName();

    @Input
    @Optional
    @Option(option = "selector", description = "Element selector; defaults to app-<component-name>")
    public abstract Property<String> getSelector();

    @Input
    @Option(option = "prefix", description = "Selector prefix (default: app); --selector takes precedence")
    public abstract Property<String> getPrefix();

    @Input
    @Option(option = "flat", description = "Generate directly in --package without a component subpackage")
    public abstract Property<Boolean> getFlat();

    @Input
    @Option(option = "inline-template", description = "Put the HTML template in the Java annotation")
    public abstract Property<Boolean> getInlineTemplate();

    @Input
    @Option(option = "inline-style", description = "Put CSS in the Java annotation")
    public abstract Property<Boolean> getInlineStyle();

    @Input
    @Option(option = "style", description = "Stylesheet format: css (default) or none")
    public abstract Property<String> getStyle();

    @Input
    @Option(option = "display-block", description = "Add :host { display: block; } to the stylesheet")
    public abstract Property<Boolean> getDisplayBlock();

    @Input
    @Option(option = "change-detection", description = "Change detection strategy: Default (default) or OnPush")
    public abstract Property<String> getChangeDetection();

    public GenerateComponent() {
        getPrefix().convention("app");
        getFlat().convention(false);
        getInlineTemplate().convention(false);
        getInlineStyle().convention(false);
        getStyle().convention("css");
        getDisplayBlock().convention(false);
        getChangeDetection().convention("Default");
    }

    /// Source root, without the package path. Defaults to `src/main/java` in the target project.
    @Internal
    public abstract DirectoryProperty getJavaDirectory();

    /// Resource root, without the package path. Defaults to `src/main/resources` in the target project.
    @Internal
    public abstract DirectoryProperty getResourcesDirectory();

    @TaskAction
    public void generate() {
        final var name = ComponentName.parse(getComponentName().getOrElse(""));
        final var basePackage = getPackageName().getOrElse("");
        final var suffix = name.packageSuffix(getFlat().get());
        final var packageName = basePackage + (suffix.isEmpty() ? "" : "." + suffix);
        if (!packageName.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")
                || !SourceVersion.isName(packageName, SourceVersion.RELEASE_25)) {
            throw new GradleException("Provide --package with a valid Java package, e.g. com.example.components.");
        }
        final var prefix = getPrefix().get();
        final var selector = getSelector().getOrElse((prefix.isEmpty() ? "" : prefix + "-") + name.fileName());
        if (!selector.matches("[a-z][a-z0-9]*(?:-[a-z0-9]+)*")) {
            throw new GradleException("--selector must be a lowercase element name, e.g. app-user-card.");
        }
        final var style = getStyle().get();
        if (!style.equals("css") && !style.equals("none")) {
            throw new GradleException("--style must be css or none; Jaspring does not compile Sass or Less.");
        }
        final var strategy = switch (getChangeDetection().get()) {
            case "Default" -> "DEFAULT";
            case "OnPush" -> "ON_PUSH";
            default -> throw new GradleException("--change-detection must be Default or OnPush.");
        };

        final var packagePath = packageName.replace('.', '/');
        final var javaPath = getJavaDirectory().get().getAsFile().toPath().resolve(packagePath);
        final var resourcePath = getResourcesDirectory().get().getAsFile().toPath().resolve(packagePath);
        final var files = new ArrayList<GeneratedFile>();
        final var template = "<p>%s works!</p>\n".formatted(name.fileName());
        final var css = getDisplayBlock().get() ? ":host {\n    display: block;\n}\n" : "";
        final var attributes = new ArrayList<String>();
        attributes.add("selector = \"" + selector + "\"");
        if (getInlineTemplate().get()) {
            attributes.add("template = \"" + template.stripTrailing() + "\"");
        } else {
            attributes.add("templateUrl = \"" + name.fileName() + ".component.html\"");
            files.add(new GeneratedFile(resourcePath.resolve(name.fileName() + ".component.html"), template));
        }
        if (style.equals("css")) {
            if (getInlineStyle().get()) {
                attributes.add("styles = \"" + css.replace("\n", "\\n") + "\"");
            } else {
                attributes.add("styleUrl = \"" + name.fileName() + ".component.css\"");
                files.add(new GeneratedFile(resourcePath.resolve(name.fileName() + ".component.css"), css));
            }
        }
        if (strategy.equals("ON_PUSH")) {
            attributes.add("changeDetection = ChangeDetectionStrategy.ON_PUSH");
        }
        files.addFirst(new GeneratedFile(javaPath.resolve(name.className() + ".java"), """
                        package %s;

                        %s\
                        import dev.isira.jaspring.component.Component;

                        @Component(
                                %s
                        )
                        public class %s {
                        }
                        """.formatted(packageName,
                        strategy.equals("ON_PUSH") ? "import dev.isira.jaspring.component.ChangeDetectionStrategy;\n" : "",
                        String.join(",\n        ", attributes), name.className())));

        for (final var file : files) {
            if (Files.exists(file.path(), LinkOption.NOFOLLOW_LINKS)) {
                throw new GradleException("Refusing to overwrite existing file: " + file.path());
            }
        }

        final var created = new ArrayList<Path>();
        try {
            for (final var file : files) {
                Files.createDirectories(file.path().getParent());
                // Reserve each path before writing so a concurrent invocation cannot overwrite it.
                Files.createFile(file.path());
                created.add(file.path());
                Files.writeString(file.path(), file.content(), StandardOpenOption.WRITE);
            }
        } catch (IOException exception) {
            for (final var path : created) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException cleanupException) {
                    exception.addSuppressed(cleanupException);
                }
            }
            throw new GradleException("Could not generate component: " + exception.getMessage(), exception);
        }
        for (final var file : files) {
            getLogger().lifecycle("Created {}", file.path());
        }
    }

    private record GeneratedFile(Path path, String content) {}

    private record ComponentName(String className, String fileName, String parentPackage) {
        String packageSuffix(boolean flat) {
            final var folder = fileName.replace("-", "");
            return flat ? parentPackage : parentPackage.isEmpty() ? folder : parentPackage + "." + folder;
        }

        static ComponentName parse(String value) {
            final var parts = value.split("/", -1);
            final var parents = new ArrayList<String>();
            for (var index = 0; index < parts.length - 1; index++) {
                if (!parts[index].matches("[A-Za-z][A-Za-z0-9]*(?:-[A-Za-z][A-Za-z0-9]*)*")) {
                    throw new GradleException("Invalid component path; use names such as admin/user-card.");
                }
                parents.add(parts[index].replace("-", "").toLowerCase(Locale.ROOT));
            }
            var base = parts[parts.length - 1];
            if (base.endsWith("Component")) {
                base = base.substring(0, base.length() - "Component".length());
            }
            if (!base.matches("[A-Za-z][A-Za-z0-9]*(?:-[A-Za-z][A-Za-z0-9]*)*")) {
                throw new GradleException("Provide --name as user-card, UserCard or UserCardComponent, optionally preceded by folders.");
            }
            final var className = new StringBuilder();
            for (final var part : base.split("-")) {
                className.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
            final var fileName = className.toString()
                    .replaceAll("([A-Z]+)([A-Z][a-z])", "$1-$2")
                    .replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                    .toLowerCase(Locale.ROOT);
            return new ComponentName(className + "Component", fileName, String.join(".", parents));
        }
    }
}
