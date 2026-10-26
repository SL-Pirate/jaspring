import { parseTemplate, DomElementSchemaRegistry } from '@angular/compiler';
import postcss from 'postcss';
import scopePlugin from './scope-plugin.mjs';

export const versions = __JASPRING_VERSIONS__;

function location(loc) {
  return { offset: loc.offset, line: loc.line, col: loc.col };
}

/// Parses an Angular template with Angular's own parser. Errors are returned as data, never thrown,
/// so Java can report all of them at once with Angular's exact wording.
export function parse(template, url, options) {
  const result = parseTemplate(template, url, options ?? {});
  return {
    nodes: result.nodes,
    ngContentSelectors: result.ngContentSelectors,
    errors: (result.errors ?? []).map((error) => ({
      message: error.msg,
      text: error.toString(),
      level: error.level,
      start: location(error.span.start),
      end: location(error.span.end),
    })),
  };
}

/// Dumps Angular's DOM schema once so Java can validate property bindings without the JS context.
export function domSchema() {
  const registry = new DomElementSchemaRegistry();
  const elements = {};
  for (const [tag, props] of registry._schema) {
    elements[tag] = Object.fromEntries(props);
  }
  const events = {};
  for (const [tag, names] of registry._eventSchema) {
    events[tag] = [...names];
  }
  const attrToProp = {};
  for (const attr of ['class', 'for', 'formaction', 'innerHtml', 'readonly', 'tabindex']) {
    attrToProp[attr] = registry.getMappedPropName(attr);
  }
  return { elements, events, attrToProp };
}

/// Applies emulated view encapsulation: every selector is scoped to the component's content attribute,
/// :host becomes the host attribute. Mirrors Angular's ShadowCss, which the compiler does not export.
export function processCss(css, scopeId) {
  const result = postcss([
    scopePlugin({ hostAttr: `_nghost-${scopeId}`, contentAttr: `_ngcontent-${scopeId}` }),
  ]).process(css, { from: undefined });
  return result.css;
}
