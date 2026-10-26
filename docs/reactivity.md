# Reactivity

`dev.isira.jaspring.signals` is a port of Angular's signal primitives (`packages/core/primitives/signals`, 21.2.x). The public API mirrors `@angular/core`:

| Angular | Jaspring |
|---|---|
| `signal(v)` / `set` / `update` / `asReadonly` | `Signals.signal(v)` → `WritableSignal<T>` |
| `computed(fn)` | `Signals.computed(supplier)` → `Signal<T>` |
| `linkedSignal(fn)` / `linkedSignal({source, computation})` | `Signals.linkedSignal(...)` |
| `effect(fn)` with `onCleanup` | `Signals.effect(body)` / `effect(cleanup -> ...)` → `EffectRef` |
| `untracked(fn)` | `Signals.untracked(...)` |
| `input()` / `input.required()` / `model()` / `output()` | `Signals.input()` / `inputRequired()` / `model()` / `output()` |

Templates call signals the Angular way: `{{ count() }}` evaluates `count.get()`.

## Graph

`signals.internal.Graph` is a line-for-line port of `graph.ts`: producer/consumer links in intrusive linked lists, version counters, a global epoch, live consumers that receive push notifications, and lazy pull for everything else. Computed values are glitch-free: a diamond recomputes once. Dependencies are rebuilt incrementally on every run, so a computed that stops reading a signal stops depending on it.

## Thread model

- The active consumer and the notification flag are thread-bound through `CurrentContext`, a `runWith`-only abstraction over `ThreadLocal` that a `ScopedValue` implementation can replace.
- Node fields are plain. A page's graph is only mutated during that page's turn, which runs under the page lock; lock hand-off gives the happens-before guarantee between turns.
- A signal captures its `SignalOwner` when created. A page binds itself as owner during turns, so writes from a thread that is not running the page's turn throw `SignalWriteOutsideTurnException`. Signals created outside any turn (singleton services) are unguarded shared state.

## Differences from Angular

- `Equality.defaultEquality()` is `Objects.equals`, not `Object.is`. Java records and collections are value types; an equal value is not a change. `Equality.identity()` restores Angular's behaviour.
- Effects need a scheduler. Inside a component or page turn `EffectContext` provides one; elsewhere pass a `QueueEffectScheduler` explicitly. Effects never run synchronously on a write.
- Writes inside `computed` throw `InvalidSignalWriteException`; effects may write, as in current Angular.
