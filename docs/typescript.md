# TypeScript

Browser scripts (client hooks, page-level functions for `Browser.call`, anything that runs in the page) can be written in TypeScript. They live in `src/main/typescript`, are type-checked and compiled by the Gradle plugin before the application runs, and are served under `/_jaspring/ts/` with a `<script type="module">` tag per entry point that the layout gets automatically. Nothing in production runs the compiler: the jar holds plain JavaScript.

```ts
// src/main/typescript/sparkline.ts
jaspring.hooks.register<HTMLCanvasElement, number[]>("sparkline", {
  mounted(el, ctx) {
    draw(el, ctx.params);
  },
  updated(el, ctx) {
    draw(el, ctx.params);
  },
  size(el) {
    return { width: el.width, height: el.height };
  },
});
```

## Layout

- `src/main/typescript/**/*.ts`: every file is compiled; nested paths are kept. Top-level files (`sparkline.ts`) are entry points and get a script tag in the page. Files in subdirectories (`lib/chart.ts`) are modules reached through imports: `import { draw } from './lib/chart.js'` (the `.js` extension, as in any ES module project; `./lib/chart.ts` is accepted and rewritten).
- `.d.ts` files under the same root add ambient types; they emit nothing.
- Output: one ES module per file under `/_jaspring/ts/<path>.js` (`sparkline.ts` → `/_jaspring/ts/sparkline.js`). No bundling, no npm packages, no `node_modules`.
- The `jaspring` global is typed (`JaspringHook<E, P>`, `JaspringHookContext<P>`, `JaspringGlobal`; also importable as types from `'jaspring'`). The typings come from the client shim's own source, so they match what runs.

The fixed compiler options: `target es2022`, `module esnext`, `moduleResolution bundler`, `strict`, `isolatedModules` with `verbatimModuleSyntax`, `lib es2022` + `dom`, every file a module. They are not configurable: the import rule, the served format and the editor setup depend on them.

## Build

With the Gradle plugin applied (see [its README](../jaspring-gradle-plugin/README.md)) two tasks run before `processResources`:

| Task                     | What it does                                                                                                           |
| ------------------------ | ---------------------------------------------------------------------------------------------------------------------- |
| `extractTypescriptTypes` | writes `jaspring.d.ts` and `tsconfig.base.json` to `build/jaspring/typescript/types/` for the editor                   |
| `compileTypescript`      | compiles `src/main/typescript`; `processResources` ships `jaspring/ts/**/*.js` and `jaspring/jaspring-typescript.json` |

Projects without a `src/main/typescript` directory skip both. The compiler is `dev.isira:jaspring-typescript`, resolved on the `jaspringTypescript` configuration (the plugin adds its own version). It bundles TypeScript 6.0.3, the last JavaScript release of the compiler (7.x is a native binary), and runs it in GraalJS inside a Gradle worker: nothing to install. A GraalVM JDK makes it several times faster; the plugin uses `GRAALVM_HOME` or the JDK running Gradle when it is GraalVM, like the WebAssembly build. Measured on GraalVM 25: about 3 s to load the compiler and 6 s for a type-checked compile of a few files, under 1 s warm; Gradle's worker daemons keep it warm between builds, and the task is cached and up-to-date like any other.

```kotlin
jaspring {
    typescript {
        sourceDir.set(layout.projectDirectory.dir("src/main/typescript"))
        typeCheck.set(true)   // -Pjaspring.typescript.typeCheck=false transpiles without checking
        host.set(dev.isira.jaspring.gradle.JaspringExtension.Typescript.Host.GRAALJS) // or NODE; -Pjaspring.typescript.host=node
        nodePath.set(file("/usr/local/bin/node")) // -Pjaspring.typescript.nodePath; default: the first Node 20+ on the PATH
        skip.set(false)        // -Pjaspring.typescript.skip=true
    }
}
```

`host = NODE` runs the same bundled compiler in a Node 20+ process, which starts and compiles faster; the output is byte-identical, so it is a developer's choice, not a project setting. Type errors fail the build in `tsc`'s format, clickable from the project root:

```
src/main/typescript/sparkline.ts(12,5): error TS2322: Type 'string' is not assignable to type 'number'.
```

## Editor

A `tsconfig.json` in the project root makes IntelliJ and VS Code check the sources with the build's options and typings. Run any build (or `./gradlew extractTypescriptTypes`) once so the extended file exists:

```json
{
  "extends": "./build/jaspring/typescript/types/tsconfig.base.json",
  "include": [
    "src/main/typescript/**/*.ts",
    "build/jaspring/typescript/types/jaspring.d.ts"
  ]
}
```

## Serving and the page

`/_jaspring/ts/**` serves the compiled files from the jar with a one-hour cache (`no-cache` in dev mode). The layout gets one tag per entry point after the shim, with the source digest in the URL so a new build is fetched at once:

```html
<script type="module" src="/_jaspring/ts/sparkline.js?v=574f4503"></script>
```

Module scripts are deferred by the HTML specification and run after the classic `jaspring.js` tag wherever they stand, so `jaspring` is always defined when they run. The tags are injected only together with the shim: with `jaspring.live.transport=off` there is no shim, no `jaspring` global, and no page scripts. Scripts linked by hand in the layout keep working; they must be `type="module"` or placed after the shim.

## Startup fallback

The build's manifest records a digest per source. With `dev.isira:jaspring-typescript` on the runtime classpath (`developmentOnly` in Gradle, `runtime` scope in Maven), the starter compares it at startup with `src/main/typescript` next to the working directory (or `classpath:/typescript/` when that directory is absent) and compiles when the manifest is missing or stale: an IDE run that bypassed Gradle, or a build without the plugin. The result is served from memory. Errors fail startup, like a template Angular rejects. A packaged application has no sources, so the manifest stands and nothing runs.

| Property                         | Default               | Meaning                           |
| -------------------------------- | --------------------- | --------------------------------- |
| `jaspring.typescript.enabled`    | `true`                | the fallback at all               |
| `jaspring.typescript.type-check` | `true`                | off transpiles without checking   |
| `jaspring.typescript.source-dir` | `src/main/typescript` | relative to the working directory |
| `jaspring.typescript.host`       | `graaljs`             | or `node`                         |

Spring devtools does not watch `.ts` files: after editing one, run `./gradlew compileTypescript` (an IDE delegating builds to Gradle does this on build) or restart the application.

## Limits

- TypeScript 6 is the end of the JavaScript compiler; language features after it would need a native host.
- No bundling and no npm imports: a script may import its sibling modules only. Third-party libraries are loaded the usual way (a `<script>` in the layout) and typed with a local `.d.ts`.
- A JVM without GraalVM's optimizing compiler runs the compiler interpreted, about twice as slow cold; the build says so. `host = NODE` avoids it where Node is installed.
