# Live protocol

The server and the client shim exchange JSON messages. The same messages travel over a WebSocket (default) or over HTTP (`POST /_jaspring/event`, `GET /_jaspring/poll`). Hooks and browser calls (see [events.md](events.md)) ride on the same messages.

## Markers in the HTML

- `data-jsp-v="<viewId>"` on every component host element. Patches target it.
- `data-jsp-on="<event>[.keys]:<ref>[:flags] ..."` on elements with event bindings. `ref` is `<viewId>.<n>` and identifies the handler occurrence and the scope it was rendered in (loop variables included). Flags: `pd` prevent default, `sp` stop propagation, `w` the handler runs a browser closure (hovering or focusing the element warms the WebAssembly runtime). A `window:` or `document:` prefix targets those objects (`window:resize:3.1`).
- `data-jsp-model="<ref> ..."` on inputs with `[(ngModel)]` or `(ngModelChange)`.
- `data-jsp-hook="<name>"` (optionally `data-jsp-hook-params`, JSON) is written by the template author; the shim runs the hook registered under that name. The server never reads it.
- The shim script tag carries `data-jsp-page` and `data-jsp-transport`, plus `data-jsp-pending="true"` when messages already wait for the client (calls made while rendering, late patches), so the HTTP shim polls at once.
- When the application has browser closures, a second tag loads `/_jaspring/jaspring-wasm.js` with `data-jsp-wasm-js` and `data-jsp-wasm-bin` (the launcher and module under `/_jaspring/app/<digest>/`) and `data-jsp-wasm-policy` (`eager` or `idle`); eager pages also carry `<link rel="preload">` hints. See [browser.md](browser.md).

## Client to server

| message | fields | meaning |
|---|---|---|
| `join` | `page` | after the socket opens, and after every reconnect |
| `event` | `page`, `h` (ref), `ev` | a DOM event (`ev` is the serialized event) or an `ngModelChange` (`ev` is the new value) |
| `result` | `page`, `id`, `value` or `error {message}` | the answer to a `call` |
| `pong` | | answer to `ping` |

Serialized event: `type`, `key`, `code`, `altKey`, `ctrlKey`, `metaKey`, `shiftKey`, `clientX`, `clientY`, `button`, `target {tagName, id, name, value, checked, dataset}`, `detail` (for `CustomEvent`s), and for `submit` a `form` object built from `FormData`. Number inputs send numbers, checkboxes booleans, multi-selects arrays.

## Server to client

| message | fields | meaning |
|---|---|---|
| `patch` | `seq`, `patches: [{v, html}]` | new outer HTML of the topmost changed views; stale `seq` values are ignored |
| `call` | `id`, `target`, `v` (view id, for hook targets), `args` | run `target` in the browser and answer with `result`; `target` is `hook.method`, a registered function name, or `wasm` (args: closure id, host view id, captured values) for a browser closure |
| `redirect` | `url` | navigate |
| `error` | `code`, `message` | `unknown-page` makes the shim reload; other codes are logged |
| `ping` | | keepalive (WebSocket only) |

Calls follow the patches of the same turn, so a hook mounted by a patch exists before a call targets it. Over HTTP a response is `{"messages": [...], "pending": bool}`; `pending` tells the shim to poll until forked work has finished and every outstanding call has been answered. `result` messages are posted to `/_jaspring/event` like events. A `410 Gone` means the page is no longer live.

## Security

A page id is 128 random bits bound to the HTTP session that rendered it; a request from another session sees `unknown-page`. Only handler expressions that exist in the template can ever run. A `result` is matched to a call the page issued, by id, inside that page only; its value is untrusted JSON handed to the component as maps, lists and scalars. Spring Security's CSRF token, when present as `_csrf` / `_csrf_header` meta tags, is sent with HTTP requests.
