# Changelog

Notable changes to Jaspring. New work goes under **Unreleased**. On release, rename that section to the version and date, update the docs site from its **Docs to update** list, and start a new empty **Unreleased** section.

## Unreleased

### Added

- JNPM browser dependencies in Gradle: `implementation`, `api`, `devImplementation` and `devApi`, frozen `jnpm.lock` installs under `build/node_modules`, managed Node/npm, esbuild bundles, private registries, ESLint, and optional IntelliJ integrations. Node/OS imports fail browser bundling. See `docs/jnpm.md`.

- Browser scripts in TypeScript. `src/main/typescript/**/*.ts` is type-checked and compiled by the Gradle plugin's new `compileTypescript` task (TypeScript 6.0.3 bundled into the new `jaspring-typescript` module and run on GraalJS in a worker; `host = NODE` runs the same bundle in Node for speed), served as ES modules under `/_jaspring/ts/`, and every top-level file gets a `<script type="module">` tag after the shim. `jaspring.d.ts` types the `jaspring` global (`JaspringHook<E, P>`, `JaspringHookContext<P>`); `extractTypescriptTypes` puts it and a `tsconfig.base.json` under `build/jaspring/typescript/types/` for editors. See `docs/typescript.md`.
- Startup fallback: with `jaspring-typescript` on the runtime classpath, the starter recompiles `src/main/typescript` at startup when the build's manifest is missing or stale (IDE runs, builds without the plugin) and serves the result from memory. `jaspring.typescript.enabled`, `type-check`, `source-dir` and `host` configure it.
- `data-jsp-ignore` attribute. A patch leaves the element's children and attributes alone and only adds or updates its `data-*` attributes, so hook params still reach `updated`. Use it for elements whose DOM a widget owns (editors, maps, date pickers, Chart.js).

### Changed

- The client shim (`jaspring.js`, `jaspring-wasm.js`) is written in TypeScript under `tools/shim` and compiled with `tsc`; the committed JavaScript is generated and checked against the sources by `shimTest`. Behaviour is unchanged. `tools/shim-tests` moved into `tools/shim`.
- The sample's sparkline hook is `src/main/typescript/sparkline.ts`; the layout no longer links it by hand.
- IntelliJ plugin 0.1.1: IntelliJ IDEA 2026.3 (build 263) is supported (`until-build` 263.*), the deprecated `Query.iterator()` use is gone, and the plugin's tests also run on 2026.3 (`-PplatformVersion=2026.3`), where two platform pieces that moved out of the core are now loaded explicitly.
- The sample builds its WebAssembly closures by default when Oracle GraalVM and Binaryen are found. `-Pjaspring.wasm=true` requires them (CI), `-Pjaspring.wasm=false` skips the build; before, the build was opt-in.

### Fixed

- A browser call result above 8 KB no longer closes the WebSocket. The server joins message fragments itself, so the container's text buffer (8 KB on Tomcat) does not apply. The new limit is 4 MiB characters per client message.
- A call result above the WebSocket limit now fails the call at once with `BrowserCallException`, instead of after the 30 s timeout.
- Large patches queued behind a slow send no longer drop the socket. The send buffer limit went from 512 KB to 16 MB.

### Docs to update

Pages in the docs site (`jaspring-docs`) that describe the last release and need changes when this ships:

- **Browser calls** (`browser-calls`, "Read the result"): remove "Keep results small" and the 8 KB warning. State the 4 MiB limit over the WebSocket. In "Handle failures", add an oversized result as a failure cause.
- **Client hooks** (`client-hooks`, "Patches and hook-managed DOM"): add `data-jsp-ignore`.
- **Third-party JavaScript** (`third-party-libraries`): use `data-jsp-ignore` on the chart in "Wrap it" and "Bind it", drop the `resize()` workaround note, and update the checklist and the "small hook results" wording. Show the wrapper hook in TypeScript with a local `.d.ts` for the library.
- New **TypeScript** page (`typescript`): source layout, build settings, GraalJS/Node compiler hosts, editor setup, serving and automatic tags, startup fallback for package-free applications, and the TypeScript 6 limit.
- New **JNPM** page: Gradle declarations, lock/update/install workflow, browser-only package compatibility, bundled assets, ESLint, private registries and IntelliJ integration.
- **Client hooks** (`client-hooks`): the example in TypeScript with `JaspringHook<HTMLCanvasElement, number[]>`, scripts injected automatically, module ordering after the shim.
- **Gradle plugin** page: the `typescript { }` block, `jaspringTypescript` configuration and the two tasks.
