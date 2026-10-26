// PostCSS plugin implementing Angular's emulated encapsulation rules.
// Keeps the same output shape as ShadowCss so stylesheets written for Angular behave the same.

const DEEP = /\s*(?:::ng-deep|\/deep\/|>>>)\s*/;

export default function scopePlugin({ hostAttr, contentAttr }) {
  return {
    postcssPlugin: 'jaspring-scope',
    Rule(rule) {
      if (isKeyframeStep(rule)) return;
      rule.selectors = rule.selectors.flatMap((selector) => scopeSelector(selector, hostAttr, contentAttr));
    },
  };
}
scopePlugin.postcss = true;

function isKeyframeStep(rule) {
  const parent = rule.parent;
  return parent && parent.type === 'atrule' && /keyframes$/i.test(parent.name);
}

function scopeSelector(selector, hostAttr, contentAttr) {
  const deep = selector.split(DEEP);
  // Everything after ::ng-deep stays unscoped, which is how Angular pierces into children.
  const scopedHead = scopeCompounds(deep[0], hostAttr, contentAttr);
  const tail = deep.slice(1).join(' ').trim();
  return scopedHead.map((head) => (tail ? `${head} ${tail}` : head));
}

// :host-context(.x) matches when an ancestor OR the host itself carries .x, so it expands to two selectors.
function scopeCompounds(selector, hostAttr, contentAttr) {
  const contextMatch = selector.match(/:host-context\(([^)]+)\)/);
  if (contextMatch) {
    const rest = selector.replace(contextMatch[0], ':host');
    const scopedRest = scopeCompounds(rest, hostAttr, contentAttr);
    return scopedRest.flatMap((scoped) => [
      scoped.replace(`[${hostAttr}]`, `${contextMatch[1]}[${hostAttr}]`),
      `${contextMatch[1]} ${scoped}`,
    ]);
  }
  return [splitCompounds(selector).map((part) => scopeCompound(part, hostAttr, contentAttr)).join('')];
}

function scopeCompound(part, hostAttr, contentAttr) {
  if (part.combinator) return part.text;
  const compound = part.text;
  const hostMatch = compound.match(/^:host(?:\(([^)]*)\))?/);
  if (hostMatch) {
    return `[${hostAttr}]${hostMatch[1] ?? ''}${compound.slice(hostMatch[0].length)}`;
  }
  const pseudoIndex = firstTopLevelColon(compound);
  if (pseudoIndex < 0) return `${compound}[${contentAttr}]`;
  return `${compound.slice(0, pseudoIndex)}[${contentAttr}]${compound.slice(pseudoIndex)}`;
}

// Splits "a > b.c:hover" into compounds and combinators, honouring brackets and parentheses.
function splitCompounds(selector) {
  const parts = [];
  let depth = 0;
  let current = '';
  const flush = () => {
    if (current) parts.push({ text: current, combinator: false });
    current = '';
  };
  for (let i = 0; i < selector.length; i++) {
    const ch = selector[i];
    if (ch === '(' || ch === '[') depth++;
    if (ch === ')' || ch === ']') depth--;
    if (depth === 0 && /[\s>+~]/.test(ch)) {
      flush();
      let combinator = ch;
      while (i + 1 < selector.length && /[\s>+~]/.test(selector[i + 1])) combinator += selector[++i];
      parts.push({ text: combinator.trim() ? ` ${combinator.trim()} ` : ' ', combinator: true });
      continue;
    }
    current += ch;
  }
  flush();
  return parts;
}

function firstTopLevelColon(compound) {
  let depth = 0;
  for (let i = 0; i < compound.length; i++) {
    const ch = compound[i];
    if (ch === '(' || ch === '[') depth++;
    else if (ch === ')' || ch === ']') depth--;
    else if (ch === ':' && depth === 0) return i;
  }
  return -1;
}
