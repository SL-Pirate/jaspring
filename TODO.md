# TODO

Known issues to fix in upcoming releases. Remove an entry when its fix ships.

## Large browser call results close the WebSocket

Found in 0.3.0. A hook method that returned a ~25 KB string (`chart.toBase64Image()`) closed the page's WebSocket, and the Java `ZoneTask` never completed. The starter does not set a WebSocket message size, so the container default applies (8 KB text messages on Tomcat). Any `result` message, and possibly any large `patch`, above that limit breaks the connection.

Fix options:
- Raise the text message limit in the starter, for example with a `ServletServerContainerFactoryBean`, and make sure patches fit too.
- Or send results above a threshold over the HTTP `/_jaspring/event` endpoint instead of the socket.
- At least fail the call with `BrowserCallException` when the socket closes, instead of leaving it open until the 30 s timeout.

Repro: a hook method returning a 25 KB string, called with `Browser.current().call("hook.method")` over the websocket transport. The docs site's browser-calls page currently tells users to keep results small; update it once this is fixed.

## No way to keep widget DOM out of a morph

Found in 0.3.0. A patch morphs the whole changed view, including hooked elements and their children. Attributes, inline styles and child nodes that a third-party widget added are removed whenever an enclosing view changes. Chart.js lost its canvas inline styles on every parent patch and only recovered because the hook's `updated` calls `resize()`. Widgets that build their own DOM (editors, maps, date pickers) must rebuild it after each patch.

Fix: add an opt-out attribute such as `data-jsp-ignore` (like Phoenix's `phx-update="ignore"`). Implement it in `jaspring.js` with idiomorph's `beforeNodeMorphed` callback, keeping the element's own attributes or its children as decided, and still calling the hook's `updated` so `data-jsp-hook-params` reach it. Document it in `docs/events.md` and in the docs site's client-hooks and third-party pages.
