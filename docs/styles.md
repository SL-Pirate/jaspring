# Styles

`@Component(styles = ...)` and `styleUrl` (resolved next to the class, like `templateUrl`) are processed once at startup by PostCSS running in GraalJS, with the Jaspring scope plugin (`tools/js-bundle/src/scope-plugin.mjs`). The plugin reproduces Angular's emulated encapsulation: every compound selector gets `[_ngcontent-jsp-cN]`, `:host` becomes `[_nghost-jsp-cN]`, `:host-context(x)` expands to both the ancestor and the host form, and everything after `::ng-deep` stays unscoped. Keyframe steps are left alone.

The renderer puts `_ngcontent-jsp-cN` on every element of the component's template and `_nghost-jsp-cN` on its host. The layout emits all processed stylesheets in one `<style data-jsp-styles>` block in `<head>`.

Adding user PostCSS plugins (autoprefixer, nesting, Tailwind) is the next step: the bundle already contains PostCSS, so it is a matter of loading plugin bundles from the classpath.
