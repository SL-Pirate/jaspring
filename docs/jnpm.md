# JNPM

JNPM installs npm packages and bundles browser scripts from Gradle. Declare dependencies in `build.gradle.kts`
or `build.gradle`; do not create a project `package.json`. Jaspring generates npm's internal workspace and
`node_modules` under `build/`. Node/npm and esbuild are managed automatically and used only at build time.

```kotlin
jnpm {
    implementation("nanoid:5.1.6")
    implementation("lodash-es:^4.17.21")
    api("@types/lodash-es:^4.17.12")
    devImplementation("eslint-plugin-import:2.31.0")
    devApi("@types/node:^22")
}
```

The same methods work in Groovy. Scoped names and npm semver ranges are supported; dist-tags, Git, file,
URL and workspace dependencies are not. Declare each package in one scope.

| Scope | Purpose |
| --- | --- |
| `implementation` | Browser runtime packages, including their own bundled typings |
| `api` | Additional declarations, including `@types` packages |
| `devImplementation` | Development tools and test packages |
| `devApi` | Types used by development tools and tests |

These scopes are independent of Java dependencies. `api` does not publish dependencies to another Gradle
project. Runtime imports need an `implementation` declaration; type-only imports may also use `api`.
Application imports of development-only or undeclared transitive packages fail.

## Browser code only

JNPM bundles ESM/CommonJS for the browser. Imports of Node/OS APIs such as `fs`, `node:fs`, `path`, `net`,
and `child_process` fail the build, including transitive imports. There are no automatic Node polyfills.
A package with separate Node and browser entrypoints works when its browser entrypoint avoids those APIs.

Development tools such as ESLint run in Node and never enter the browser bundle. Dependency lifecycle scripts
are disabled; packages requiring postinstall compilation or native Node addons are outside this contract.

## Lock and build

```sh
./gradlew jnpmLock
# Commit jnpm.lock alongside the Gradle declarations.
./gradlew build
```

`jnpmLock` reconciles declarations with the existing lock, retaining compatible resolutions. `jnpmUpdate`
explicitly refreshes versions within the declared ranges. Normal builds use `npm ci`: missing or mismatched
locks fail with instructions to run `jnpmLock`. Normal builds never update the committed lock.

| Task | Purpose |
| --- | --- |
| `jnpmLock` | Create or reconcile the committed lock |
| `jnpmUpdate` | Update versions within declared ranges |
| `jnpmInstall` | Install the exact lock into `build/node_modules` |
| `prepareJnpmTypes` | Generate declaration snapshots, editor configuration and IDE metadata |
| `compileTypescript` | Type-check and bundle browser entrypoints |
| `lintTypescript` | Run ESLint; included in `check` and `build` |
| `fixTypescript` | Explicitly apply ESLint fixes |

Each application module owns its declarations and lock. Use qualified tasks such as `:app:jnpmLock` in
multi-project builds. `clean` removes the installation and generated metadata. Tool downloads and npm's cache
survive under Gradle's user cache, allowing offline reinstalls after an online build. Offline cache misses fail.

For a centrally managed toolchain, set `jnpm.nodeHome` or `-Pjnpm.nodeHome=/path/to/node` to an unpacked Node
distribution containing Node 22.15+ on the 22.x line, or Node 24+, and npm. The default is a checksum-pinned Node 22.23.1 distribution.
Managed distributions cover Linux, macOS and Windows on x64 and ARM64.

## TypeScript and assets

Keep entrypoints directly under `src/main/typescript`, with supporting modules in subdirectories. The existing
TypeScript compiler performs checking; esbuild emits minified ES2022 modules, shared chunks, source maps,
CSS and referenced static assets with hashed names. JSON and literal dynamic imports are supported. Bundle
CSS is loaded eagerly once per page.

Only browser output, source maps, license notices and the manifest enter the application JAR. Failed checking
or bundling preserves the preceding successful bundle. Successful rebuilds remove obsolete chunks.

Extend the generated JNPM configuration from the application's `tsconfig.json`:

```json
{
  "extends": "./build/jaspring/typescript/types/tsconfig.jnpm.json",
  "include": ["src/main/typescript/**/*.ts"]
}
```

Run `prepareJnpmTypes` first. Public package entrypoints and subpaths are resolved with TypeScript itself,
then mapped to installed declarations. Do not use a blanket `paths` alias into `build/node_modules` or pass
the editor's declaration mappings to a bundler.

`tsconfig.jnpm.dev.json` also exposes development scopes. A separate test/tool tsconfig can extend it;
JNPM does not supply a test runner. Browser code does not automatically receive Node globals.

JNPM applications build before startup, including IDE runs. With the development compiler present, missing
or stale bundles produce an instruction to run `compileTypescript`. Packages are never installed at startup.
Applications without JNPM retain the existing source-only startup compiler.

## ESLint and IntelliJ

Linting is enabled by default, using ESLint and TypeScript ESLint recommended rules with browser globals and
`jaspring`. Errors fail `check`; warnings are reported. Normal lint tasks never rewrite sources.

```kotlin
jnpm {
    lint { enabled.set(false) } // optional
}
```

An optional root `eslint.config.mjs` replaces the defaults. Its imports resolve against the JNPM installation;
declare additional plugins in `devImplementation`.

```js
import js from "@eslint/js";
import ts from "typescript-eslint";
import globals from "globals";

export default [
  js.configs.recommended,
  ...ts.configs.recommended,
  { languageOptions: { globals: { ...globals.browser, jaspring: "readonly" } } },
  { rules: { "@typescript-eslint/no-explicit-any": "off" } },
];
```

JNPM pins ESLint, its standard config, TypeScript ESLint, TypeScript, esbuild and globals. Conflicting overrides
of these toolchain packages are rejected.

The IntelliJ plugin imports JNPM's Gradle model, prepares dependencies after sync, and registers package
directories as libraries. With JavaScript/TypeScript support installed, it provides ESLint diagnostics and
fixes for unsaved editor text alongside IntelliJ's TypeScript completion and navigation. Application run
configurations receive a Gradle frontend build step. Existing template features still work without Gradle
or JavaScript plugins.

Use **Tools → JNPM** to prepare dependencies after `clean`, reconcile the lock, or update dependencies.
A missing lock must be reconciled explicitly; IDE sync does not create one.

## Private registries

```kotlin
jnpm {
    registries {
        create("company") {
            url.set(uri("https://npm.example.com/"))
            scopes.add("@company")
            token.set(providers.environmentVariable("COMPANY_NPM_TOKEN"))
        }
    }
}
```

A registry without scopes replaces npmjs.org. Registry names use letters, digits and underscores; scopes must
be unique. HTTPS is required except for loopback registry fixtures. Credentials are injected into npm's
environment; generated npm configuration contains environment-variable references. Keep token values in the
environment or private Gradle configuration, never in project scripts. User/global npm configuration is not
inherited: registry selection comes from Gradle.

The first release targets browser packages. JSX setup, HMR, npm publishing and JavaScript test execution remain
separate work.
