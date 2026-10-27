# Jaspring Gradle plugin

Registers `generateComponent`, an explicit scaffolding task. It creates editable
Java sources and package-relative HTML/CSS resources; it does not run during normal
compilation or add application dependencies. Run Gradle with JDK 25. Tested with
the repository's Gradle 9.8.2 wrapper.

## Setup

The plugin is published to Maven Central alongside Jaspring's
libraries. Add Central to **plugin** repositories in
`settings.gradle.kts` (ordinary dependency repositories do not resolve plugins):

```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

Apply the matching release in the application's `build.gradle.kts`:

```kotlin
plugins {
    java
    id("dev.isira.jaspring") version "0.2.0"
}
```

Keep the usual `jaspring-spring-boot-starter` dependency. The Gradle plugin supplies
build tooling; the starter supplies the runtime.

To try unpublished changes from a local checkout, run
`./gradlew :jaspring-gradle-plugin:publishToMavenLocal`
in the Jaspring checkout, add `mavenLocal()` to the consumer's `pluginManagement.repositories`,
and use version `0.0.1-SNAPSHOT`. See [publishing](../docs/publishing.md).

## Generate

```sh
./gradlew generateComponent --name=user-card --package=com.example.components
```

Creates:

```text
src/main/java/com/example/components/usercard/UserCardComponent.java
src/main/resources/com/example/components/usercard/user-card.component.html
src/main/resources/com/example/components/usercard/user-card.component.css
```

The class has `@Component(selector = "app-user-card", templateUrl = "user-card.component.html",
styleUrl = "user-card.component.css")`. The template starts with `<p>user-card works!</p>`;
CSS starts empty. Existing files are never overwritten. If any target already exists,
the task fails before writing any files. Files created during a failed write are
removed where possible.

`UserCard` and `UserCardComponent` are also accepted. Like Angular CLI, a component
gets its own directory by default; Java requires valid package names, so kebab-case
folder names become lowercase package segments without hyphens (`user-card` →
`usercard`). `--name=admin/user-card` creates the `admin.usercard` subpackage.
`--flat` omits the final component directory, retaining explicit parent folders.

```sh
./gradlew generateComponent --name=user-card --package=com.example.components --flat
./gradlew generateComponent --name=counter --package=com.example.components --inline-template --inline-style --display-block --change-detection=OnPush
./gradlew :app:generateComponent --name=admin/user-card --package=com.example.components --prefix=shop
```

In multi-project builds, apply the plugin to each application module and use a
qualified task such as `:app:generateComponent` to target one module.

| Option | Default | Meaning |
|---|---|---|
| `--name` | required | Kebab-case or Java class name, optionally with parent folders |
| `--package` | required | Base Java package, preferably under the application's scan package |
| `--flat` | false | Omit the component subpackage |
| `--prefix` | `app` | Selector prefix; an empty value omits the prefix |
| `--selector` | derived | Explicit element selector, overriding the prefix |
| `--inline-template` | false | Put HTML in `@Component.template` instead of a file |
| `--inline-style` | false | Put CSS in `@Component.styles` instead of a file |
| `--style` | `css` | `css` or `none`; `none` omits styles, including inline styles |
| `--display-block` | false | Add `:host { display: block; }` to CSS |
| `--change-detection` | `Default` | `Default` or `OnPush` |

Boolean options use flags (`--flat`, `--inline-template`); Gradle also provides
their `--no-...` forms. List task options with `./gradlew help --task generateComponent`.
Gradle's global `--dry-run` skips task execution; it does not preview generated contents.

The options follow [Angular CLI](https://angular.dev/cli/generate/component) where
they map to Jaspring. Jaspring retains its `Component` class suffix, `.component.html`
filenames and `Default` change detection. TypeScript, NgModule and standalone options
do not apply. This generator does not create test stubs, controllers or routes,
and does not compile Sass/Less. It does not require Node or Angular CLI.

## Browser closures (WebAssembly)

When the `java` plugin is applied, the plugin also compiles every `Platform.onBrowser` lambda into one WebAssembly module (see [docs/browser.md](../docs/browser.md)):

| Task | What it does |
|---|---|
| `scanBrowserClosures` | finds `BrowserTask` lambdas in the compiled classes, prepares rewritten class copies and the generated entry point |
| `compileBrowserDispatcher` | compiles the entry point with the GraalVM JDK against `jaspring-browser-runtime` |
| `buildBrowserImage` | runs `native-image --tool:svm-wasm`; `processResources` ships `jaspring/wasm/jaspring-app.js`, `jaspring-app.js.wasm` and `jaspring/jaspring-browser.json` |

Projects without closures skip the last two tasks. Requirements: Oracle GraalVM 25.2.x (with Web Image) and Binaryen `wasm-as` 119+ on the PATH; a missing tool fails the build with the fix in the message.

```kotlin
jaspring {
    browser {
        skip.set(false)                     // -Pjaspring.browser.skip=true builds without the image
        graalvmHome.set(file("/opt/graalvm")) // default: GRAALVM_HOME, else the JDK running Gradle when it is GraalVM
        assemblerPath.set(file("/usr/bin/wasm-as"))
        nativeImageArgs.add("-H:+ReportImageSizeBreakdown")
        imageClasspath.from(configurations.runtimeClasspath.get().filter { it.name.startsWith("commons-text") })
    }
}
```

The `jaspringBrowser` configuration holds `jaspring-browser-runtime`; the plugin adds its own version by default.

## Build configuration defaults

Set a default package or adjust output roots in the consuming build:

```kotlin
tasks.named<dev.isira.jaspring.gradle.GenerateComponent>("generateComponent") {
    packageName.set("com.example.components")
    // Only needed when the application uses nonstandard source roots:
    javaDirectory.set(layout.projectDirectory.dir("src/main/java"))
    resourcesDirectory.set(layout.projectDirectory.dir("src/main/resources"))
}
```

The CLI overrides configured naming defaults. Output roots default to
`src/main/java` and `src/main/resources` in the target module; configure both this
task and the application's source sets when using another layout.

## Development

The sample applies the plugin by ID and sets its default package to
`dev.isira.jaspring.sample.components`:

```sh
./gradlew :jaspring-sample:generateComponent --name=profile-card
```

`buildSrc` compiles the same plugin sources before the main build is configured,
so the sample works from a checkout without publishing or downloading Jaspring's
plugin. The `jaspring-gradle-plugin` module remains responsible for its tests and
publication.

```sh
./gradlew :jaspring-gradle-plugin:check :jaspring-gradle-plugin:publishAllPublicationsToBuildRepository
```

TestKit tests run isolated consumer builds, compile generated Java against Jaspring,
and cover configuration caching, options, file conflicts and invalid input.
