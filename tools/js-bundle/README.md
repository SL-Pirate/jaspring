# js-bundle

Builds the two JavaScript bundles GraalJS loads on the JVM:

- `jaspring-core/src/main/resources/jaspring/js/jaspring-compiler.mjs`: Angular's `@angular/compiler`, PostCSS, Tailwind and the Jaspring scope plugin, loaded at application startup.
- `jaspring-typescript/src/main/resources/jaspring/typescript/jaspring-typescript.mjs`: the TypeScript compiler (`typescript` 6.x, the last JavaScript release; 7.x is a native binary) with the `es2022` and `dom` lib declarations and an in-memory compiler host (`src/typescript.mjs`). The Gradle plugin and the starter's startup fallback run it on GraalJS or under Node; the build imports the result under Node once to prove it loads outside a browser.

```
pnpm install
pnpm build
```

Commit the generated bundles and the `META-INF/jaspring/THIRD-PARTY-LICENSES.txt` next to each. The build collects complete license texts from packages contributing code to a bundle. `BundleTest` in jaspring-core and `TypescriptCompilerTest` in jaspring-typescript fail when a bundle's version does not match `gradle/libs.versions.toml`, so bump both together. The fixed compiler options live in `src/typescript-options.mjs`; `jaspring-typescript/src/main/resources/jaspring/typescript/tsconfig.base.json` must carry the same values (a test checks).
