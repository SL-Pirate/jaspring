import { test } from 'node:test';
import assert from 'node:assert/strict';
import { load, settle, PAGE } from './harness.mjs';

test('click on a bound element sends a serialized event with the handler ref', async () => {
  const h = load({ body: '<app-x data-jsp-v="1"><button id="b" data-jsp-on="click:1.1">+</button></app-x>' });
  h.open();
  h.fire(h.document.getElementById('b'), 'click');
  await settle();

  const [event] = h.sentOverSocket();
  assert.equal(event.t, 'event');
  assert.equal(event.page, PAGE);
  assert.equal(event.h, '1.1');
  assert.equal(event.ev.type, 'click');
  assert.equal(event.ev.target.tagName, 'button');
  assert.equal(event.ev.target.id, 'b');
});

test('custom events bubble to a binding and carry their detail', async () => {
  const h = load({ body: '<app-x data-jsp-v="1"><canvas id="c" data-jsp-on="point-picked:1.1"></canvas></app-x>' });
  h.open();
  h.document.getElementById('c').dispatchEvent(new h.window.CustomEvent('point-picked', { bubbles: true, detail: { index: 3 } }));
  await settle();

  const [event] = h.sentOverSocket();
  assert.equal(event.h, '1.1');
  assert.deepEqual(event.ev.detail, { index: 3 });
});

test('key bindings match Angular key syntax with modifiers', async () => {
  const h = load({ body: '<app-x data-jsp-v="1"><input id="i" data-jsp-on="keyup.control.enter:1.1"></app-x>' });
  h.open();
  const input = h.document.getElementById('i');
  input.dispatchEvent(new h.window.KeyboardEvent('keyup', { bubbles: true, key: 'Enter' }));
  input.dispatchEvent(new h.window.KeyboardEvent('keyup', { bubbles: true, key: 'Enter', ctrlKey: true }));
  await settle();

  const sent = h.sentOverSocket();
  assert.equal(sent.length, 1, 'only the control+enter press matched');
  assert.equal(sent[0].ev.ctrlKey, true);
});

test('window-targeted bindings listen on the window', async () => {
  const h = load({ body: '<app-x data-jsp-v="1"><div data-jsp-on="window:resize:1.1"></div></app-x>' });
  h.open();
  h.window.dispatchEvent(new h.window.Event('resize'));
  await settle();

  assert.equal(h.sentOverSocket()[0].h, '1.1');
});

test('ngModel inputs send their value and a patch echoing it leaves the input alone', async () => {
  const h = load({ body: '<app-x data-jsp-v="1"><input id="n" value="" data-jsp-model="1.2"><i></i></app-x>' });
  h.open();
  const input = h.document.getElementById('n');
  input.focus(); // the user is typing: the morph must not touch the active element's value
  input.value = 'An';
  h.fire(input, 'input');
  input.value = 'Ann';
  h.fire(input, 'input');
  await settle();

  const sent = h.sentOverSocket();
  assert.deepEqual(sent.map((m) => m.ev), ['An', 'Ann']);

  // The server echoes the first value while the user already typed more: the input must keep "Ann".
  h.deliver({ t: 'patch', seq: 1, patches: [{ v: 1, html: '<app-x data-jsp-v="1"><input id="n" value="An" data-jsp-model="1.2"><i>An</i></app-x>' }] });
  await settle();
  assert.equal(h.document.getElementById('n').value, 'Ann');
  assert.equal(h.document.querySelector('i').textContent, 'An');

  // A value the client never sent is a model change and the input follows it.
  h.deliver({ t: 'patch', seq: 2, patches: [{ v: 1, html: '<app-x data-jsp-v="1"><input id="n" value="Bob" data-jsp-model="1.2"><i>Bob</i></app-x>' }] });
  await settle();
  assert.equal(h.document.getElementById('n').value, 'Bob');
});
