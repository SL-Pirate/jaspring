# js-bundle

Builds `jaspring-core/src/main/resources/jaspring/js/jaspring-compiler.mjs`: Angular's `@angular/compiler`, PostCSS and the Jaspring scope plugin in one ES module that GraalJS loads at startup.

```
pnpm install
pnpm build
```

Commit the generated bundle and `jaspring-core/src/main/resources/META-INF/jaspring/THIRD-PARTY-LICENSES.txt`. The build collects complete license texts from packages contributing code to the bundle. `BundleTest` in jaspring-core fails when the bundle's versions do not match `gradle/libs.versions.toml`, so bump both together.
