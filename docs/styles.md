# Styles

`@Component(styles = ...)` and `styleUrl` (resolved next to the class, like `templateUrl`) are processed once at startup by PostCSS running in GraalJS, with the Jaspring scope plugin (`tools/js-bundle/src/scope-plugin.mjs`). The plugin reproduces Angular's emulated encapsulation: every compound selector gets `[_ngcontent-jsp-cN]`, `:host` becomes `[_nghost-jsp-cN]`, `:host-context(x)` expands to both the ancestor and the host form, and everything after `::ng-deep` stays unscoped. Keyframe steps are left alone.

The renderer puts `_ngcontent-jsp-cN` on every element of the component's template and `_nghost-jsp-cN` on its host. The layout emits all processed stylesheets in one `<style data-jsp-styles>` block in `<head>`.

Adding user PostCSS plugins (autoprefixer, nesting, Tailwind) is the next step: the bundle already contains PostCSS, so it is a matter of loading plugin bundles from the classpath.

## Tailwind

Set `jaspring.tailwind.stylesheet=classpath:tailwind.css` (a file with `@import "tailwindcss";` and your `@theme`) and Tailwind v4's engine runs in the same GraalJS runtime at startup. Tailwind's file scanner needs Node, so it is replaced by `TailwindCandidates`: the class names found in the compiled templates (static `class` attributes, `[class.x]`, string literals and object keys in `[class]`/`[ngClass]`, directive `class.x` host bindings, and the layout's `class` attributes). Only those utilities are generated, into a `<style data-jsp-tailwind>` block in `<head>`.

Component styles may use `@apply` and theme variables; they are compiled with `@reference` to your stylesheet before the scope plugin runs, so `:host { @apply block rounded-lg; }` works. `@plugin` and `@config` are not available because no Node code runs here.

This is Tailwind's core, not its PostCSS plugin: the plugin is a Node wrapper around the same engine. PostCSS still does the scoping afterwards.
