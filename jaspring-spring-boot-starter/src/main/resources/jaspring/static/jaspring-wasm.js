/* Jaspring WebAssembly loader. Loads the Web Image launcher built by the Gradle plugin, runs it once per page, and
   answers the server's "wasm" calls through jaspring.calls. The script tag carries data-jsp-wasm-js (launcher URL),
   data-jsp-wasm-bin (module URL, beside the launcher) and data-jsp-wasm-policy ("eager" or "idle"). */
(function () {
  'use strict';

  var script = document.currentScript;
  var launcherUrl = script && script.getAttribute('data-jsp-wasm-js');
  var moduleUrl = script && script.getAttribute('data-jsp-wasm-bin');
  var policy = (script && script.getAttribute('data-jsp-wasm-policy')) || 'idle';
  if (!window.jaspring || !launcherUrl) return;

  // A module with one empty struct type: valid only where WebAssembly GC is supported.
  var GC_PROBE = new Uint8Array([0, 97, 115, 109, 1, 0, 0, 0, 1, 3, 1, 95, 0]);
  var supported = typeof WebAssembly === 'object' && typeof WebAssembly.validate === 'function' && WebAssembly.validate(GC_PROBE);
  var loading = null;
  var ready = false;

  function ensure() {
    if (loading) return loading;
    loading = new Promise(function (resolve, reject) {
      if (!supported) { reject(new Error('This browser does not support WebAssembly GC')); return; }
      var tag = document.createElement('script');
      tag.src = launcherUrl;
      tag.onload = function () {
        var vm = window.JaspringWasm;
        if (!vm || typeof vm.run !== 'function') { reject(new Error('The WebAssembly launcher did not define JaspringWasm')); return; }
        // GraalVM.run(args, config): a Config instance carries the module path and the launcher's other defaults.
        var config = typeof vm.Config === 'function' ? new vm.Config() : {};
        config.wasm_path = moduleUrl;
        Promise.resolve(vm.run([], config)).then(function () {
          if (!window.jaspringWasm || typeof window.jaspringWasm.run !== 'function') {
            reject(new Error('The WebAssembly module did not install its dispatcher'));
            return;
          }
          ready = true;
          resolve();
        }, reject);
      };
      tag.onerror = function () { reject(new Error('Failed to load ' + launcherUrl)); };
      document.head.appendChild(tag);
    });
    return loading;
  }

  function warm() {
    if (supported) ensure().catch(function () { /* reported when a call needs it */ });
  }

  // Warms the HTTP cache without instantiating: the launcher's own fetch is then a cache hit.
  function prefetch() {
    if (!supported || loading) return;
    try {
      fetch(launcherUrl, { priority: 'low', credentials: 'same-origin' }).catch(function () { });
      fetch(moduleUrl, { priority: 'low', credentials: 'same-origin' }).catch(function () { });
    } catch (e) { /* older fetch without priority */ }
  }

  window.jaspring.calls.register('wasm', function (id, host, args) {
    return ensure().then(function () {
      return window.jaspringWasm.run(id, host, args);
    }).then(function (envelope) {
      if (envelope && envelope.error) throw new Error(envelope.error);
      return envelope ? envelope.value : null;
    });
  });

  window.jaspring.wasm = { ensure: ensure, warm: warm, loaded: function () { return ready; } };

  if (policy === 'eager') {
    warm();
  } else {
    var idle = window.requestIdleCallback || function (fn) { setTimeout(fn, 1500); };
    window.addEventListener('load', function () { idle(prefetch); });
  }
})();
