# Architecture

Jaspring = **Ja**va + **Spri**ng + A**ng**ular. Templates use Angular's HTML template syntax unchanged; components, pipes, directives and services are Java on Spring.

## Modules

- `jaspring-core`: the framework. Signals, zone, change detection, the template pipeline, the component model, live pages. Depends on Spring Framework (DI) and GraalJS.
- `jaspring-browser-runtime`: the browser side of `Platform.onBrowser` closures: the `BrowserClient` facade implemented with GraalVM Web Image's `@JS` interop. Compiles only on Oracle GraalVM and runs only inside the WebAssembly module.
- `jaspring-typescript`: the TypeScript compiler (6.x, bundled) hosted in GraalJS or Node, with the `jaspring.d.ts` typings. Used by the Gradle plugin at build time and by the starter's startup fallback.
- `jaspring-spring-boot-starter`: Spring Boot auto-configuration, the MVC `View`, the live-event transports (WebSocket, HTTP), the client shim, the WebAssembly loader and the serving of compiled TypeScript.
- `jaspring-gradle-plugin`: component scaffolding, WebAssembly builds, TypeScript checking, and JNPM's npm installation, esbuild bundling, linting and Gradle tooling model.
- `jaspring-sample`: a demo application.
- `tools/js-bundle`: the Node project that bundles `@angular/compiler` and PostCSS into one ESM file committed under `jaspring-core/src/main/resources/jaspring/js/`, and the TypeScript compiler into one under `jaspring-typescript`. Framework developers maintain these bundles; JNPM provisions its own build toolchain for applications.
- `tools/shim`: the client shim's TypeScript sources, built with `tsc` into the starter (committed), the `jaspring.d.ts` typings, and jsdom tests of the built shim. Framework developers only.

## Pipeline

1. Startup: Spring classpath scanning finds `@Component` / `@Pipe` / `@Directive`. Each template is parsed by Angular's real compiler running in GraalJS, converted to Java records, resolved and validated. Component CSS is scoped through PostCSS. Templates that Angular would reject fail startup with Angular's own messages.
2. Request: a controller returns a `JaspringView`. A `LivePage` renders the component tree inside a zone on a virtual thread, waits for zone-tracked work to settle, and the layout wraps the HTML.
3. Events: a small client shim sends DOM events to the server (WebSocket by default, HTTP fallback, or disabled). The page's handler runs inside the zone, change detection decides which component views changed, and only those are sent back as HTML patches.
4. Browser work: the server can call registered JavaScript hooks and functions, or run Java closures compiled to WebAssembly, and gets the answer back over the same transport as a `result` that completes a task inside a turn (see [browser.md](browser.md)). Hooks and functions are written in TypeScript, compiled at build time and served as ES modules (see [typescript.md](typescript.md)).

## Fixed choices

- GraalVM is the target JDK. GraalJS runs only at startup on the server; the server never evaluates JavaScript on the request path. The browser runs the shim and, when the application uses `Platform.onBrowser`, a Web Image build of those closures.
- Signals and zone-based change detection are both first-class. The current zone and the current reactive consumer are thread-bound (ThreadLocal today, ScopedValue later).
- Virtual threads carry requests, forked work and live pages. Rendering itself is sequential; one mutator per page.
- TypeScript is compiled by the bundled TypeScript 6 compiler on GraalJS at build time (Node is an optional, faster host for the same bundle). 6.x is the last JavaScript release of the compiler; 7.x is a native binary, out of reach of the dependency rule, so the supported language is the 6.x line.
- JNPM is build-time tooling: Java owns the Gradle workflow, npm resolves dependencies, esbuild bundles browser code and ESLint checks it. Gson handles tooling metadata and Commons Compress extracts the pinned Node distribution. These tools are absent from packaged applications.
