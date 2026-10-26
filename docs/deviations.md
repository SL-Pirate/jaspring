# Deviations from Angular

Template syntax is Angular's own, parsed by Angular's compiler, so syntax compatibility is complete by construction. Behaviour differs in these places, on purpose or until a later step:

- **Global registry.** Every scanned component, pipe and directive is visible to every template. Angular's standalone `imports` lists are not enforced yet.
- **Equality.** Signals and input change detection use `Objects.equals` (`Equality.defaultEquality()`), because Java records and collections are value types. Angular uses identity; `Equality.identity()` restores it.
- **Numbers.** Template arithmetic produces `Long` when integral and `Double` otherwise; values are coerced to the declared Java type when they reach methods, fields or typed signals.
- **preventDefault / stopPropagation** are decided when the element renders, from the handler expression (`false` at the end, or explicit `$event.preventDefault()` / `$event.stopPropagation()` calls). `submit` always prevents the default.
- **Zone.** Nothing is monkey-patched. Only turns, `zone.run`, `fork` continuations and `wrap`ped runnables are "inside the zone". Blocking calls on the virtual request thread are already inside.
- **Effects** belong to the page and run before the views on every tick pass; Angular's view-effect timing is not reproduced. Effects created outside a turn need an explicit scheduler.
- **Lifecycle order within siblings.** Each child runs its pre-render hooks and renders before the next sibling is processed; Angular batches the hook phases across the children of one view. Within one component the order is Angular's.
- **ICU plurals** know only English categories (`=N`, `one`, `other`). `number`, `percent`, `currency` and `date` format with `java.text` / `java.time` and the JDK's locale data, not Angular's CLDR data; named date formats outside English use the JDK's localized styles.
- **Sanitizer.** `[innerHTML]` goes through a port of Angular's allowlist over a small tolerant tokenizer rather than a browser DOM parser.
- **`#ref` on plain elements** resolves to an `ElementRef` holding the tag and rendered attributes; there is no DOM node on the server.
- **`@defer`** renders its placeholder (or the main block when hydrate triggers are present); triggers are never fired on the server.
- **Dev-mode `ExpressionChangedAfterItHasBeenChecked`** second pass is not implemented yet.
- **No parallel rendering.** One page is mutated by one thread at a time; virtual threads carry requests, forked work and pages, not sibling subtrees.
