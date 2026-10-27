# Templates

Templates are Angular templates. They are parsed by Angular's own compiler (`@angular/compiler`, bundled and run in GraalJS at startup), so anything Angular rejects fails startup with Angular's message, and anything Angular accepts has a defined meaning here.

## Pipeline

1. `GraalAngularTemplateParser` runs `parseTemplate` and converts the result to the records in `template.ast` (`TemplateNode`) and `template.expr` (`Expr`), one record per Angular class with the same fields. `ASTWithSource` wrappers are unwrapped.
2. `ExpressionEvaluator` interprets expressions against the component instance with JavaScript semantics (see below).
3. `TemplateRenderer` walks the nodes and writes HTML through `HtmlWriter`.

`templateUrl` and `styleUrl` are classpath resources resolved relative to the component's package, the way Angular resolves them relative to the component file: `card.html`, `./card.html` and `../shared/card.html` all work, and a leading `/` is classpath-absolute. Components can live in any subpackage; keep their files next to the class.

## Expression semantics

- Numbers are `Long` when integral and `Double` otherwise; results are normalized so `6 / 3` is `2`, not `2.0`. Values passed to Java methods or stored into typed signals are coerced to the declared type, including through generics (`WritableSignal<Integer>.set`).
- `==` is JavaScript's loose equality, `===` strict. `null` and `undefined` are distinct; both render as empty text in interpolation.
- Truthiness follows JavaScript: `0`, `""`, `null`, `undefined`, `false` and `NaN` are falsy, everything else (including empty lists) is truthy.
- `a?.b.c` short-circuits the whole chain to `null` when `a` is nullish, as Angular's generated code does.
- Members resolve in this order: template locals, then public fields, getters (`getX`/`isX`), zero-argument accessors (records), map keys, and finally methods (`x(...)`). Component classes also expose private fields to their own template.
- Strings, lists and arrays get the JavaScript members Angular templates use (`length`, `includes`, `slice`, `map`, `filter`, `toUpperCase`, ...). Arrow functions work and adapt to Java functional interfaces.
- Signals are called like functions: `count()` reads the signal and registers the dependency.
- Assignments exist only inside event handlers. Assigning to a member that holds a `WritableSignal` writes through the signal, so `[(ngModel)]="name"` works for both fields and signals.

## Rendering rules

- Text and interpolation are escaped (`& < >`); attribute values also escape `"`.
- `[prop]` bindings are reflected to the attribute the browser would parse them from (`[value]` → `value="..."`, `[disabled]` → present or absent, `[htmlFor]` → `for`). Property types come from Angular's DOM schema. `[innerHTML]` is sanitized unless the value is a `SafeHtml`; `[textContent]` replaces the children with escaped text.
- Security contexts come from Angular's parser: URL-typed attributes (`href`, `src`, ...) go through the port of Angular's URL sanitizer, resource URLs (`iframe src`) require `SafeResourceUrl`, HTML goes through the port of Angular's HTML sanitizer.
- `class` and `style` merge from the static attribute, `[class]`/`[style]`, `[class.x]`/`[style.x.unit]`, `[ngClass]`/`[ngStyle]`, later and more specific bindings winning.
- `<ng-container>` renders its children only. Angular's namespace prefixes (`:svg:circle`) are stripped.
- `#ref` on a plain element resolves to an `ElementRef` with the tag name and rendered attributes; `@let` values are visible to later siblings in the same view.
- `data-jsp-hook="name"` is an ordinary attribute the client shim reads to run a registered hook on the element (see [events.md](events.md)); custom events a hook dispatches are bound like any other event: `(point-picked)="pick($event.detail.index)"`.
- Event bindings render as `data-jsp-on="event:ref[:flags]"` markers when the host registers handlers. The flags are decided statically: `pd` when the handler ends in `false` or calls `$event.preventDefault()`, `sp` for `$event.stopPropagation()`. Without a live host no marker is rendered.

## Control flow

- `@if` / `@else if` / `@else` render the first truthy branch; `@if (x; as y)` binds the alias inside the branch.
- `@for` iterates lists, arrays, iterables and map entries, exposes `$index`, `$first`, `$last`, `$even`, `$odd`, `$count` and their aliases, and renders `@empty` for empty input. The `track` expression keys the iteration, which is what keeps child component instances stable across renders.
- `@switch` compares with `===` and groups consecutive empty `@case`s; `@default` renders when nothing matches.
- `@defer` renders its `@placeholder` on the server (nothing without one), unless it declares `hydrate` triggers, in which case the main content renders, matching Angular SSR.
- ICU expansions (`{count, plural, =0 {none} other {{{ count }} items}}`, `select`) render the matching case. Plural categories are English only (`one` for exactly 1) until CLDR data is added.
- `<ng-template>` renders nothing by itself; `#ref` on it yields a `TemplateRef`. `*ngIf` (with `then`/`else`/`as`), `*ngFor` (with `index`/`first`/`last`/`even`/`odd`/`count` and `trackBy`), `[ngSwitch]` with `*ngSwitchCase`/`*ngSwitchDefault`, and `*ngTemplateOutlet` with `context` behave like Angular's built-in directives. An outlet renders in the scope the template was declared in.
- Template references are hoisted per embedded view, so an `else` template may follow the element that uses it. Each embedded view (block body, loop iteration, outlet) carries a key derived from the template position and the `track` value.
