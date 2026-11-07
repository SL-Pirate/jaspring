import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { load, settle, PAGE } from './harness.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const LOADER = readFileSync(path.resolve(here, '../../../jaspring-spring-boot-starter/src/main/resources/jaspring/static/jaspring-wasm.js'), 'utf8');

function withLoader(h, { policy = 'idle', gc = true } = {}) {
  h.window.WebAssembly = { validate: () => gc };
  const tag = h.document.createElement('script');
  tag.setAttribute('data-jsp-wasm-js', '/_jaspring/app/abc/jaspring-app.js');
  tag.setAttribute('data-jsp-wasm-bin', '/_jaspring/app/abc/jaspring-app.js.wasm');
  tag.setAttribute('data-jsp-wasm-policy', policy);
  Object.defineProperty(h.document, 'currentScript', { value: tag, configurable: true });
  h.window.eval(LOADER);
  // The injected launcher tag never loads in jsdom; simulate the Web Image launcher when asked to.
  h.launch = (dispatcher) => {
    const injected = h.document.querySelector('script[src="/_jaspring/app/abc/jaspring-app.js"]');
    assert.ok(injected, 'launcher script injected');
    h.window.JaspringWasm = { run: () => { h.window.jaspringWasm = { run: dispatcher }; return Promise.resolve(); } };
    injected.onload();
  };
}

const HOST = '<app-x data-jsp-v="1"><button id="b" data-jsp-on="click:1.1:w">go</button></app-x>';

test('wasm calls wait for the runtime and unwrap its envelopes', async () => {
  const h = load({ body: HOST });
  h.open();
  withLoader(h, { policy: 'eager' });
  h.deliver({ t: 'call', id: '1', target: 'wasm', args: ['closure-id', 1, ['Colombo']] });
  h.deliver({ t: 'call', id: '2', target: 'wasm', args: ['bad', 1, []] });
  await settle();
  assert.equal(h.sentOverSocket().length, 0, 'nothing answered before the runtime is ready');

  h.launch((id, host, args) => (id === 'bad' ? { error: 'unknown closure bad' } : { value: { id, host, args } }));
  await settle();

  assert.deepEqual(h.sentOverSocket(), [
    { t: 'result', page: PAGE, id: '1', value: { id: 'closure-id', host: 1, args: ['Colombo'] } },
    { t: 'result', page: PAGE, id: '2', error: { message: 'unknown closure bad' } },
  ]);
  assert.equal(h.jaspring.wasm.loaded(), true);
});

test('engines without WebAssembly GC answer wasm calls with an error', async () => {
  const h = load({ body: HOST });
  h.open();
  withLoader(h, { policy: 'eager', gc: false });
  h.deliver({ t: 'call', id: '3', target: 'wasm', args: ['x', 1, []] });
  await settle();
  assert.match(h.sentOverSocket()[0].error.message, /WebAssembly GC/);
  assert.equal(h.document.querySelector('script[src="/_jaspring/app/abc/jaspring-app.js"]'), null, 'no launcher injected');
});

test('the idle policy does not load until a handler with the w flag is hovered', async () => {
  const h = load({ body: HOST });
  h.open();
  withLoader(h, { policy: 'idle' });
  assert.equal(h.document.querySelector('script[src="/_jaspring/app/abc/jaspring-app.js"]'), null);

  h.document.getElementById('b').dispatchEvent(new h.window.Event('pointerover', { bubbles: true }));
  await settle();
  assert.ok(h.document.querySelector('script[src="/_jaspring/app/abc/jaspring-app.js"]'), 'hover warmed the runtime');
});
