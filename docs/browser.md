# Browser interop

Jaspring renders on the server, but some work only the browser can do: draw on a canvas, read `localStorage`, ask for the user's location, wrap a JavaScript widget. There are two ways to reach it, built on the same protocol.

- **Hooks and calls** are plain JavaScript. Use them for widgets, third-party libraries and pages where every kilobyte counts. See [events.md](events.md#client-hooks).
- **Browser closures** are Java lambdas compiled to WebAssembly at build time with GraalVM Web Image and run in the browser. Use them for application pages where one language is worth the runtime download.

## Browser closures

```java
@Component(selector = "app-browser", templateUrl = "browser.component.html")
public class BrowserComponent implements OnInit {
    public final WritableSignal<String> theme = Signals.signal("...");

    @Override
    public void ngOnInit() {
        Platform.onBrowser(client -> client.localStorage().get("theme").orElse("light"))
                .thenAccept(theme::set);
    }

    public void locate() {
        Platform.onBrowserAsync(Position.class, client -> client.geolocation().current())
                .whenComplete((position, error) -> ...);
    }
}
```

`Platform.onBrowser` takes a `BrowserTask`: a lambda that receives a `BrowserClient` and returns a value. The server identifies the lambda (through Java's serializable-lambda machinery) and sends its id and captured values to the browser, where the compiled body runs with the real DOM and returns JSON. The result completes the `ZoneTask` inside a page turn, so continuations may write signals and the changed views go out as patches, exactly like a `fork`.

`Platform.onBrowserAsync(type, task)` is for closures that end in a browser promise (`geolocation().current()`, `clipboard().readText()`, `notifications().requestPermission()`). The class token names the promise's value type, which erasure hides from both the server and the build; `then` on a `BrowserPromise` maps the value in the browser before it is sent.

### What a closure may do

- It runs in the browser, so it cannot touch Spring beans, signals, services or `this`. The build fails naming the method when a lambda uses instance members: copy what it needs into local variables first.
- Captured values and the result travel as JSON: primitives and their boxes, `String`, enums, records of those, and arrays. `List`, `Map<String, T>` and `Optional` are allowed inside records; at the top level erasure hides their element type, so wrap them in a record. Never capture secrets; everything is visible in the page.
- `client.host()` is the component's host element. DOM writes are transient: the next patch morphs the host from server HTML. Lasting state belongs in signals.
- `client.host().dispatch("type", detail)` sends a bubbling custom event the template receives with `(type)="handle($event.detail)"`.
- Static state of the component class is reachable (static fields and initialisers are compiled in); treat it as a hazard.

The facade (`dev.isira.jaspring.browser`): `host()` (DOM queries, attributes, classes, text, values, focus, bounds, custom events), `localStorage()`, `sessionStorage()`, `cookies()`, `location()`, `geolocation()`, `clipboard()`, `notifications()`, `vibration()`.

### Build requirements

The Jaspring Gradle plugin compiles closures when the `java` plugin is applied:

1. `scanBrowserClosures` finds every `BrowserTask` lambda in the compiled classes and prepares the build inputs.
2. `compileBrowserDispatcher` compiles the generated entry point against `jaspring-browser-runtime`.
3. `buildBrowserImage` runs `native-image --tool:svm-wasm` and writes `jaspring-app.js`, `jaspring-app.js.wasm` and `jaspring/jaspring-browser.json` into the application's resources.

Requirements: Oracle GraalVM 25.2.x (Web Image is part of Oracle GraalVM, not GraalVM Community) and Binaryen's `wasm-as` 119 or later on the PATH (Fedora: `dnf install binaryen`; macOS: `brew install binaryen`; Ubuntu 24.04 packages an older version, use a [release](https://github.com/WebAssembly/binaryen/releases)). Settings: `jaspring { browser { skip; graalvmHome; assemblerPath; nativeImageArgs; imageClasspath } }`, or the Gradle properties `jaspring.browser.skip`, `jaspring.browser.graalvmHome`, `jaspring.browser.assemblerPath`. The image compiles the application's classes, Jaspring's jars and the browser runtime; a closure that calls into another library needs that jar on `imageClasspath` (library jars are excluded by default because their Native Image metadata, Spring's and Truffle's in particular, does not apply to the WebAssembly backend).

Without the toolchain the build fails with a message naming what to install; `-Pjaspring.browser.skip=true` builds without the image, and `Platform.onBrowser` then throws `BrowserClosuresUnavailableException` with the same explanation. The manifest records a digest of every class a closure was compiled from, so a class recompiled by an IDE or devtools without the image fails with "stale build" instead of running the wrong code. The application needs `jaspring-browser-runtime` on the `jaspringBrowser` configuration; the plugin adds its own version by default.

Web Image is experimental in GraalVM 25; the generated API may change between GraalVM lines. Keep `graal` in `gradle/libs.versions.toml` and the GraalVM used to build in step.

### Loading

The runtime is one module per application. The server decides per page how to load it:

- **eager** when a component rendered on the page runs a closure while it mounts: `<link rel="preload">` hints in the head and immediate loading, so the result arrives shortly after first paint;
- **idle** when the application has closures but none run at mount time: the files are prefetched after `load` during idle time, and the runtime is instantiated on the first call or when the user hovers or focuses a handler that runs a closure (its marker carries the `w` flag);
- nothing when no build exists.

Assets are served from `/_jaspring/app/<digest>/` with immutable caching; the digest covers both files, so a new build gets new URLs. Browsers need WebAssembly GC and exception handling (current Chrome, Firefox and Safari 18.2+); elsewhere calls fail with a clear `BrowserCallException` instead of hanging.

A call issued while the page is first rendered travels with the document and never delays it.

### Measured

A spike with one `@JS` DOM call, one promise and a dispatcher installed on `globalThis` (Oracle GraalVM 25.2.4, Binaryen 126, Fedora, 8 cores):

| metric | value |
|---|---|
| `native-image --tool:svm-wasm` wall time | 25 s |
| build peak memory | 0.9 GB |
| module (`.js.wasm`) | 1.34 MB raw, 487 KB gzip |
| launcher (`.js`) | 109 KB |
| `GraalVM.run` until the dispatcher is installed (Node 24, module from a local HTTP server) | 260-290 ms |

Node 24 runs the module without `--experimental-wasm-exnref`. The module size is dominated by the Java runtime, so an application with a dozen closures costs about the same as one. Browser numbers are read from the sample with DevTools open.

## Trying it in the sample

```
./gradlew :jaspring-sample:bootRun -Pjaspring.wasm=true
```

1. **Sparkline hook**: the canvas draws at load; "Add point" redraws through `updated`; clicking a bar reports the index through a custom event; "Measure" shows the canvas size (hook method) and the viewport (page-level function). In DevTools the WebSocket frames show `call`, `result`, `patch`.
2. **Browser closures**: the theme from `localStorage` appears after load without delaying first paint; "Toggle theme" writes `localStorage` and toggles a class on the host; "Where am I?" asks for geolocation; "Copy position" writes the clipboard. The Network tab shows `jaspring-app.js` and `.wasm` fetched once from `/_jaspring/app/<digest>/`, served from cache on reload.
3. Without `-Pjaspring.wasm=true` the card explains that no WebAssembly build is available.
