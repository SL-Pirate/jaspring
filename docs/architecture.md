# Architecture

Jaspring = **Ja**va + **Spri**ng + A**ng**ular. Templates use Angular's HTML template syntax unchanged; components, pipes, directives and services are Java on Spring.

## Modules

- `jaspring-core`: the framework. Signals, zone, change detection, the template pipeline, the component model, live pages. Depends on Spring Framework (DI) and GraalJS.
- `jaspring-browser-runtime`: the browser side of `Platform.onBrowser` closures: the `BrowserClient` facade implemented with GraalVM Web Image's `@JS` interop. Compiles only on Oracle GraalVM and runs only inside the WebAssembly module.
- `jaspring-spring-boot-starter`: Spring Boot auto-configuration, the MVC `View`, the live-event transports (WebSocket, HTTP), the client shim and the WebAssembly loader.
- `jaspring-gradle-plugin`: component scaffolding, and the WebAssembly build of browser closures (scan, generate, `native-image --tool:svm-wasm`).
- `jaspring-sample`: a demo application.
- `tools/js-bundle`: the Node project that bundles `@angular/compiler` and PostCSS into one ESM file committed under `jaspring-core/src/main/resources/jaspring/js/`. Only framework developers need Node.
- `tools/shim-tests`: Node tests of the client shim against a jsdom document. Framework developers only.

## Pipeline

1. Startup: Spring classpath scanning finds `@Component` / `@Pipe` / `@Directive`. Each template is parsed by Angular's real compiler running in GraalJS, converted to Java records, resolved and validated. Component CSS is scoped through PostCSS. Templates that Angular would reject fail startup with Angular's own messages.
2. Request: a controller returns a `JaspringView`. A `LivePage` renders the component tree inside a zone on a virtual thread, waits for zone-tracked work to settle, and the layout wraps the HTML.
3. Events: a small client shim sends DOM events to the server (WebSocket by default, HTTP fallback, or disabled). The page's handler runs inside the zone, change detection decides which component views changed, and only those are sent back as HTML patches.
4. Browser work: the server can call registered JavaScript hooks and functions, or run Java closures compiled to WebAssembly, and gets the answer back over the same transport as a `result` that completes a task inside a turn (see [browser.md](browser.md)).

## Fixed choices

- GraalVM is the target JDK. GraalJS runs only at startup on the server; the server never evaluates JavaScript on the request path. The browser runs the shim and, when the application uses `Platform.onBrowser`, a Web Image build of those closures.
- Signals and zone-based change detection are both first-class. The current zone and the current reactive consumer are thread-bound (ThreadLocal today, ScopedValue later).
- Virtual threads carry requests, forked work and live pages. Rendering itself is sequential; one mutator per page.
- Dependencies: Spring, GraalVM (including Web Image) and PostCSS are trusted. Everything else is written in-house or vendored as an owned copy.
