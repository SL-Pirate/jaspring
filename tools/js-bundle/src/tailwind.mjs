import { compile } from 'tailwindcss';
import indexCss from 'tailwindcss/index.css';
import themeCss from 'tailwindcss/theme.css';
import preflightCss from 'tailwindcss/preflight.css';
import utilitiesCss from 'tailwindcss/utilities.css';

const sheets = {
  'tailwindcss': indexCss,
  'tailwindcss/index.css': indexCss,
  'tailwindcss/theme': themeCss, 'tailwindcss/theme.css': themeCss, './theme.css': themeCss,
  'tailwindcss/preflight': preflightCss, 'tailwindcss/preflight.css': preflightCss, './preflight.css': preflightCss,
  'tailwindcss/utilities': utilitiesCss, 'tailwindcss/utilities.css': utilitiesCss, './utilities.css': utilitiesCss,
};

/// The application's own stylesheet is served under this id, so component styles can `@reference` its theme.
export const APP_STYLESHEET = 'jaspring:app';

function loader(appStylesheet) {
  return async function loadStylesheet(id, base) {
    const content = id === APP_STYLESHEET ? appStylesheet : sheets[id];
    if (content === undefined) throw new Error(`@import "${id}" is not available: only Tailwind's own stylesheets can be imported here`);
    return { path: 'virtual:' + id, base, content };
  };
}

async function loadModule() {
  throw new Error('@plugin and @config are not supported: Tailwind runs without Node here');
}

/// Compiles a Tailwind stylesheet for the given class candidates (one per line; Java arrays are opaque here).
/// The engine is async but does no I/O, so its promise settles in the microtask queue before control returns
/// to Java; the holder is read afterwards.
export function compileTailwind(css, candidates, appStylesheet) {
  const holder = { css: null, error: null };
  const list = String(candidates || '').split('\n').filter(Boolean);
  compile(css, { base: '/', loadStylesheet: loader(appStylesheet || ''), loadModule })
    .then((compiler) => { holder.css = compiler.build(list); })
    .catch((error) => { holder.error = String(error && error.message ? error.message : error); });
  return holder;
}
