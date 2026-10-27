# Events

Event bindings are Angular's: `(click)="save()"`, `(keyup.enter)="go()"`, `(window:resize)="layout()"`, `[(ngModel)]="name"`, `(saved)="onSaved($event)"` on components.

## What happens on the server

Rendering an event binding registers a handler on the view: the expression and the scope it was rendered in. The element gets a `data-jsp-on` marker with the handler reference. When the shim sends the event, the page runs a turn: the handler expression is evaluated with `$event` bound (a map of the serialized event, so `$event.target.value` works), the view and its ancestors are marked dirty, change detection runs, and the changed views go back as patches.

Two-way bindings (`[(ngModel)]`, `[(value)]` on components) assign the incoming value to the bound expression, writing through a `WritableSignal` when the member holds one.

## preventDefault and stopPropagation

The server cannot answer synchronously, so these are decided when the element renders: a handler that ends in `false` or calls `$event.preventDefault()` gets the `pd` flag, one that calls `$event.stopPropagation()` gets `sp`, and the shim applies them before sending. `submit` always prevents the default navigation.

## Outputs

`(saved)="handle($event)"` on a component element subscribes the parent's handler to the child's `OutputEmitter`. Emitting runs the handler immediately in the parent's scope, inside the current turn.

## Client hooks

Some behaviour belongs in the browser: drawing on a canvas, wrapping a JavaScript widget, reading layout. Put a `data-jsp-hook="name"` attribute on the element (and, if useful, `data-jsp-hook-params` with JSON, for example `[attr.data-jsp-hook-params]="points() | json"`) and register the hook in a page script:

```js
jaspring.hooks.register('sparkline', {
  mounted(el, ctx) { draw(el, ctx.params); el.onclick = e => ctx.pushEvent('point-picked', { index: 2 }); },
  updated(el, ctx) { draw(el, ctx.params); },
  destroyed(el) { },
  size(el) { return { width: el.width, height: el.height }; }
});
```

The shim calls `mounted` when a hooked element is in the document (at load, after a patch adds it, or when the hook is registered later), `updated` after a patch morphs the element's view (`ctx.params` is re-read first), and `destroyed` when the element leaves the document. Every method receives `(el, ctx, ...args)`; `ctx` has `page`, `el`, `name`, `params` and `pushEvent(type, detail)`.

`pushEvent` dispatches a bubbling `CustomEvent`, so the component receives it through an ordinary binding: `<canvas data-jsp-hook="sparkline" (point-picked)="pick($event.detail.index)">`. Nothing on the server knows about hooks; only the element attribute and the event binding exist in the template.

A patch morphs the whole changed view, hooked elements and their children included, so attributes, inline styles and child nodes that a script added are removed. Add `data-jsp-ignore` to an element whose DOM a widget owns (editors, maps, date pickers, Chart.js canvases). A patch then keeps its children and its attributes, and only adds or updates its `data-*` attributes, so new `data-jsp-hook-params` still reach `updated`. Removing `data-jsp-ignore` from the template lets the next patch morph the element again. Give the element a stable `id` so the morph always matches it to the same node.

```html
<div id="editor" data-jsp-hook="editor" data-jsp-ignore [attr.data-jsp-hook-params]="doc() | json"></div>
```

## Calling the browser

A component can ask the browser for a value from inside a turn (a constructor, lifecycle hook, event handler or fork continuation):

```java
Browser.current().call("sparkline.size").thenAccept(size -> status.set("canvas " + size));
Browser.current().call("viewport").whenComplete((value, error) -> ...);
```

`hook.method` runs that method of the hook mounted inside the component's host element; a bare name runs a function registered with `jaspring.calls.register('viewport', () => ({ width: innerWidth }))`. Functions may return a promise. The returned `ZoneTask` completes inside a page turn with the JSON value the client returned (maps, lists, strings, numbers, booleans or null; up to 4 MiB characters over the WebSocket), so continuations may write signals and the resulting patches go out as usual. The page is unstable until the answer arrives, so the HTTP transport keeps polling; a call made while the page is first rendered travels with the document and does not delay it. A client error, an unregistered target, a result above the WebSocket limit or 30 seconds without an answer fail the task with `BrowserCallException`; destroying the page cancels open calls. With `jaspring.live.transport=off` every call fails at once.

## Browser closures

`Platform.onBrowser(client -> ...)` runs a Java lambda in the browser, compiled to WebAssembly at build time, and completes like a call. See [browser.md](browser.md).

## Transports

`jaspring.live.transport=websocket` (default) opens one socket per page and pushes patches from forked work as they finish; if the socket cannot connect the shim falls back to HTTP. `http` posts each event and polls while work is pending, including while a browser call is open. `off` renders plain pages: handlers are still compiled and validated, but no markers, shim or live page exist.
