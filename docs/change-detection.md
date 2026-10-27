# Change detection

A page is a `LivePage`: the component tree, a `Zone`, an effect scheduler and the page-scoped beans, kept alive between requests. All work on it happens in turns.

## Turns

A turn takes the page lock, binds the zone, the signal owner, the effect scheduler and the `jaspring-page` scope, runs its body, runs change detection, and collects patches. The initial render, every event and every forked completion is a turn. Turns of one page never overlap; different pages run concurrently on virtual threads.

## Zone

`Zone.current()` is available inside any turn. `fork(work)` runs `work` on a virtual thread outside the zone and delivers its result as a new turn, so `thenAccept(users::set)` writes signals under the page lock and triggers change detection. The initial render waits (`whenStable`) until no fork is pending, up to `stability-timeout`; completions that arrive later are pushed to the client as patches. Browser calls (`Browser.call`, `Platform.onBrowser`) are pending work too, except during the initial render: a call made then travels with the document and counts as pending only once the render has finished.

Nothing is monkey-patched. "Inside the zone" means exactly: a turn, `zone.run`, a `fork` continuation, or a `wrap`ped runnable. Because turns run on virtual threads, plain blocking calls in `ngOnInit` or a handler are already inside the zone; `fork` is for loading in parallel or for not blocking the first response. Signals created during a turn remember their page and throw `SignalWriteOutsideTurnException` when written from any other thread.

## Modes and strategies

- `jaspring.change-detection=zone` (default): every turn ends with a full tick. Default views re-render; OnPush views re-render only when an input changed, an event fired inside them, a signal they read changed, or `markForCheck` was called. This mirrors Angular with zone.js.
- `zoneless`: only dirty views re-render. A signal written in a turn marks exactly the views that read it; nothing else is touched.
- Each view owns a reactive consumer. Signals read while its template renders link to that consumer, so a later write marks the view dirty. Hooks and handlers run untracked.
- Effects created in constructors or hooks belong to the page and run at the start of each tick pass, before the views. Angular's view-effect timing (after the owning view's template) is not reproduced yet.
- The tick repeats while effects or renders dirty something else, and gives up after ten passes like Angular.
- `ChangeDetectorRef.current()` in a constructor or hook gives `markForCheck`, `detectChanges`, `detach` and `reattach` for that view.

## Patches

The parent renders a child's host tag; the child renders its template with `\u0000id\u0000` markers where its own children go. `ViewTree.assemble` joins the cached pieces, so a clean OnPush view costs nothing to include. After a turn, the topmost views whose HTML or host tag changed become patches (`data-jsp-v` id plus outer HTML), and the shim morphs them into the live DOM.
