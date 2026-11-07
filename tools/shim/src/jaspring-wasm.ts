/* Jaspring WebAssembly loader. Loads the Web Image launcher built by the Gradle plugin, runs it once per page, and
   answers the server's "wasm" calls through jaspring.calls. The script tag carries data-jsp-wasm-js (launcher URL),
   data-jsp-wasm-bin (module URL, beside the launcher) and data-jsp-wasm-policy ("eager" or "idle").
   Source: tools/shim/src/jaspring-wasm.ts; this file is generated, do not edit. */
(function () {
  "use strict";

  const script = document.currentScript;
  const launcherUrl = (script && script.getAttribute("data-jsp-wasm-js")) || "";
  const moduleUrl = (script && script.getAttribute("data-jsp-wasm-bin")) || "";
  const policy =
    (script && script.getAttribute("data-jsp-wasm-policy")) || "idle";
  const jaspring = window.jaspring;
  if (!jaspring || !launcherUrl) return;

  // A module with one empty struct type: valid only where WebAssembly GC is supported.
  const GC_PROBE = new Uint8Array([
    0, 97, 115, 109, 1, 0, 0, 0, 1, 3, 1, 95, 0,
  ]);
  const supported =
    typeof WebAssembly === "object" &&
    typeof WebAssembly.validate === "function" &&
    WebAssembly.validate(GC_PROBE);
  let loading: Promise<void> | null = null;
  let ready = false;

  function ensure(): Promise<void> {
    if (loading) return loading;
    loading = new Promise<void>(function (resolve, reject) {
      if (!supported) {
        reject(new Error("This browser does not support WebAssembly GC"));
        return;
      }
      const tag = document.createElement("script");
      tag.src = launcherUrl;
      tag.onload = function () {
        const vm = window.JaspringWasm;
        if (!vm || typeof vm.run !== "function") {
          reject(
            new Error("The WebAssembly launcher did not define JaspringWasm"),
          );
          return;
        }
        // GraalVM.run(args, config): a Config instance carries the module path and the launcher's other defaults.
        const config: { wasm_path?: string } =
          typeof vm.Config === "function" ? new vm.Config() : {};
        config.wasm_path = moduleUrl;
        Promise.resolve(vm.run([], config)).then(function () {
          if (
            !window.jaspringWasm ||
            typeof window.jaspringWasm.run !== "function"
          ) {
            reject(
              new Error(
                "The WebAssembly module did not install its dispatcher",
              ),
            );
            return;
          }
          ready = true;
          resolve();
        }, reject);
      };
      tag.onerror = function () {
        reject(new Error("Failed to load " + launcherUrl));
      };
      document.head.appendChild(tag);
    });
    return loading;
  }

  function warm(): void {
    if (supported)
      ensure().catch(function () {
        /* reported when a call needs it */
      });
  }

  // Warms the HTTP cache without instantiating: the launcher's own fetch is then a cache hit.
  function prefetch(): void {
    if (!supported || loading) return;
    try {
      fetch(launcherUrl, { priority: "low", credentials: "same-origin" }).catch(
        function () {},
      );
      fetch(moduleUrl, { priority: "low", credentials: "same-origin" }).catch(
        function () {},
      );
    } catch (e) {
      /* older fetch without priority */
    }
  }

  jaspring.calls.register(
    "wasm",
    function (id: string, host: unknown, args: unknown[]) {
      return ensure()
        .then(function () {
          return window.jaspringWasm!.run(id, host, args);
        })
        .then(function (envelope) {
          if (envelope && envelope.error) throw new Error(envelope.error);
          return envelope ? envelope.value : null;
        });
    },
  );

  jaspring.wasm = {
    ensure: ensure,
    warm: warm,
    loaded: function () {
      return ready;
    },
  };

  if (policy === "eager") {
    warm();
  } else {
    const idle =
      typeof window.requestIdleCallback === "function"
        ? function (fn: () => void) {
            window.requestIdleCallback(fn);
          }
        : function (fn: () => void) {
            setTimeout(fn, 1500);
          };
    window.addEventListener("load", function () {
      idle(prefetch);
    });
  }
})();
