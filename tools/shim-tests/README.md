# shim-tests

Unit tests for the client shim `jaspring-spring-boot-starter/src/main/resources/jaspring/static/jaspring.js`. They load the shipped `idiomorph.min.js` and `jaspring.js` unchanged into a jsdom document with fake WebSocket and fetch transports, and run with Node's built-in test runner.

```
pnpm install
pnpm test
```

The Gradle task `:jaspring-spring-boot-starter:shimTest` runs the same command as part of `check` when Node is on the PATH and `node_modules` exists; otherwise it is skipped with a message. CI always runs it. Node is needed only by framework developers, as with `tools/js-bundle`.
