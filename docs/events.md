# Events

Event bindings are Angular's: `(click)="save()"`, `(keyup.enter)="go()"`, `(window:resize)="layout()"`, `[(ngModel)]="name"`, `(saved)="onSaved($event)"` on components.

## What happens on the server

Rendering an event binding registers a handler on the view: the expression and the scope it was rendered in. The element gets a `data-jsp-on` marker with the handler reference. When the shim sends the event, the page runs a turn: the handler expression is evaluated with `$event` bound (a map of the serialized event, so `$event.target.value` works), the view and its ancestors are marked dirty, change detection runs, and the changed views go back as patches.

Two-way bindings (`[(ngModel)]`, `[(value)]` on components) assign the incoming value to the bound expression, writing through a `WritableSignal` when the member holds one.

## preventDefault and stopPropagation

The server cannot answer synchronously, so these are decided when the element renders: a handler that ends in `false` or calls `$event.preventDefault()` gets the `pd` flag, one that calls `$event.stopPropagation()` gets `sp`, and the shim applies them before sending. `submit` always prevents the default navigation.

## Outputs

`(saved)="handle($event)"` on a component element subscribes the parent's handler to the child's `OutputEmitter`. Emitting runs the handler immediately in the parent's scope, inside the current turn.

## Transports

`jaspring.live.transport=websocket` (default) opens one socket per page and pushes patches from forked work as they finish; if the socket cannot connect the shim falls back to HTTP. `http` posts each event and polls while work is pending. `off` renders plain pages: handlers are still compiled and validated, but no markers, shim or live page exist.
