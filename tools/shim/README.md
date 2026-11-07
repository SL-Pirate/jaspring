# shim

The client shim in TypeScript. `src/jaspring.ts` and `src/jaspring-wasm.ts` are compiled with `tsc` into `jaspring-spring-boot-starter/src/main/resources/jaspring/static/`, where the starter serves them; `src/public.d.ts` is the typing of the `jaspring` browser global for application scripts and is copied to `jaspring-typescript/src/main/resources/jaspring/typescript/jaspring.d.ts`. All three outputs are committed.

```
pnpm install
pnpm build   # type-checks and writes the three files
pnpm test    # jsdom tests of the built shim, plus a check that the committed files match the sources
```

The tests load the shipped `idiomorph.min.js` and `jaspring.js` unchanged into a jsdom document with fake WebSocket and fetch transports, and run with Node's built-in test runner. `test/build.test.mjs` fails when `src/` was edited without `pnpm build`, or the generated files were edited by hand.

The Gradle task `:jaspring-spring-boot-starter:shimTest` runs `pnpm test` as part of `check` when Node is on the PATH and `node_modules` exists; otherwise it is skipped with a message. CI always runs it. Node is needed only by framework developers, as with `tools/js-bundle`.
