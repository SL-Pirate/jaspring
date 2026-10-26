# Jaspring

**Ja**va + **Spri**ng + A**ng**ular. Write components, pipes, directives and services in Java on Spring Boot; write the templates in Angular's HTML template syntax, unchanged. Pages render on the server and stay live: a small client shim sends DOM events back, the server runs change detection and returns HTML patches.

```java
@Component(selector = "app-counter", templateUrl = "counter.component.html")
public class CounterComponent {
    public final WritableSignal<Integer> count = Signals.signal(0);
    public final Signal<Integer> doubled = Signals.computed(() -> count.get() * 2);
}
```

```html
<button (click)="count.set(count() + 1)">+</button>
<span>{{ count() }}</span> doubled: {{ doubled() }}
@if (count() > 5) { <p>That is more than five.</p> }
```

```java
@Controller
class HomeController {
    @GetMapping("/")
    JaspringView home() { return JaspringView.of(HomeComponent.class).input("title", "Hello"); }
}
```

## What is in the box

- Angular's real compiler (`@angular/compiler` 21.x) runs in GraalJS at startup, so every template Angular accepts has a defined meaning here and every template Angular rejects fails startup with Angular's message. Nothing on the request path touches JavaScript.
- Signals (`signal`, `computed`, `effect`, `linkedSignal`, `input`, `model`, `output`) ported from Angular's reactive graph, plus zone and zoneless change detection with Default and OnPush strategies.
- Control flow (`@if`, `@for`, `@switch`, `@defer`, `@let`), ICU, `*ngIf`/`*ngFor`/`ngSwitch`/`ngTemplateOutlet`, `ngClass`/`ngStyle`/`ngModel`, content projection, Angular's built-in pipes, custom attribute directives, emulated style encapsulation through PostCSS, and Tailwind v4 compiled on the JVM from the class names in your templates (`@apply` included).
- Live events over a WebSocket per page with an HTTP fallback, or plain server rendering with `jaspring.live.transport=off`.
- Spring DI everywhere: components are created through the bean factory, services inject as usual, and `@Scope("jaspring-page")` gives one instance per page.

## Modules

| module | purpose |
|---|---|
| `jaspring-core` | signals, zone, change detection, template pipeline, component model, live pages |
| `jaspring-spring-boot-starter` | auto-configuration, `JaspringView`, layout, transports, client shim |
| `jaspring-gradle-plugin` | `generateComponent` task for Java classes, HTML templates and CSS |
| `jaspring-sample` | demo app: counter, todos, async users with OnPush cards, pipes |
| `tools/js-bundle` | builds the committed Angular compiler + PostCSS bundle (framework developers only) |
| `tools/intellij-plugin` | standalone IntelliJ IDEA support for Java-backed templates |

## Use in a Spring Boot application

Requires Java 25 and Spring Boot 4.1.x. Once a release is available on Maven Central,
add the starter (replace `VERSION` with the published release):

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("dev.isira:jaspring-spring-boot-starter:VERSION")
}
```

For Maven:

```xml
<dependency>
    <groupId>dev.isira</groupId>
    <artifactId>jaspring-spring-boot-starter</artifactId>
    <version>VERSION</version>
</dependency>
```

The starter includes core and the GraalJS runtime transitively. Add Spring Boot's
`spring-boot-starter-websocket` if you want WebSocket transport; otherwise Jaspring
uses HTTP. Run with `--enable-native-access=ALL-UNNAMED` for GraalJS native access.
GraalVM JDK 25 enables the optimizing runtime; other Java 25 JDKs use the interpreter.

For local installation and Maven Central releases, see [Publishing](docs/publishing.md).

## Generate a component

Apply the `dev.isira.jaspring` Gradle plugin, then run:

```sh
./gradlew generateComponent --name=user-card --package=com.example.components
```

This creates `UserCardComponent.java`, `user-card.component.html` and
`user-card.component.css` in matching Java/resource package paths. Familiar Angular
CLI options include `--flat`, `--prefix`, `--inline-template`, `--inline-style` and
`--change-detection=OnPush`. See [Gradle plugin setup and options](jaspring-gradle-plugin/README.md).

## Run the sample

```
./gradlew :jaspring-sample:bootRun
```

Open http://localhost:8080/. Requires a GraalVM JDK 25 for the optimizing JavaScript runtime; other JDKs work with a warning and slower startup.

## Docs

For IntelliJ completion, navigation and template refactoring, see [the IDE plugin](tools/intellij-plugin/README.md).

`docs/architecture.md`, `docs/templates.md`, `docs/reactivity.md`, `docs/change-detection.md`, `docs/events.md`, `docs/protocol.md`, `docs/styles.md`, `docs/deviations.md`.

## Status

Experimental. Server-side rendering with live events is working end to end; static generation, incremental regeneration and a client-side runtime (GraalWasm) are later steps.
