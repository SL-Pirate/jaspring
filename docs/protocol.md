# Live protocol

The server and the client shim exchange JSON messages. The same messages travel over a WebSocket (default) or over HTTP (`POST /_jaspring/event`, `GET /_jaspring/poll`). A future client runtime (GraalWasm) only has to speak this.

## Markers in the HTML

- `data-jsp-v="<viewId>"` on every component host element. Patches target it.
- `data-jsp-on="<event>[.keys]:<ref>[:flags] ..."` on elements with event bindings. `ref` is `<viewId>.<n>` and identifies the handler occurrence and the scope it was rendered in (loop variables included). Flags: `pd` prevent default, `sp` stop propagation. A `window:` or `document:` prefix targets those objects (`window:resize:3.1`).
- `data-jsp-model="<ref> ..."` on inputs with `[(ngModel)]` or `(ngModelChange)`.
- The shim script tag carries `data-jsp-page` and `data-jsp-transport`.

## Client to server

| message | fields | meaning |
|---|---|---|
| `join` | `page` | after the socket opens, and after every reconnect |
| `event` | `page`, `h` (ref), `ev` | a DOM event (`ev` is the serialized event) or an `ngModelChange` (`ev` is the new value) |
| `pong` | | answer to `ping` |

Serialized event: `type`, `key`, `code`, `altKey`, `ctrlKey`, `metaKey`, `shiftKey`, `clientX`, `clientY`, `button`, `target {tagName, id, name, value, checked, dataset}`, and for `submit` a `form` object built from `FormData`. Number inputs send numbers, checkboxes booleans, multi-selects arrays.

## Server to client

| message | fields | meaning |
|---|---|---|
| `patch` | `seq`, `patches: [{v, html}]` | new outer HTML of the topmost changed views; stale `seq` values are ignored |
| `redirect` | `url` | navigate |
| `error` | `code`, `message` | `unknown-page` makes the shim reload; other codes are logged |
| `ping` | | keepalive (WebSocket only) |

Over HTTP a response is `{"messages": [...], "pending": bool}`; `pending` tells the shim to poll until forked work has finished. A `410 Gone` means the page is no longer live.

## Security

A page id is 128 random bits bound to the HTTP session that rendered it; a request from another session sees `unknown-page`. Only handler expressions that exist in the template can ever run. Spring Security's CSRF token, when present as `_csrf` / `_csrf_header` meta tags, is sent with HTTP requests.
