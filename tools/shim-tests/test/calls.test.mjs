import { test } from 'node:test';
import assert from 'node:assert/strict';
import { load, settle, PAGE } from './harness.mjs';

const HOST = '<app-x data-jsp-v="1"><canvas id="a" data-jsp-hook="spark" width="240" height="60"></canvas></app-x>';

test('a page-level function answers with its value, including async functions', async () => {
  const h = load({ body: HOST });
  h.open();
  h.jaspring.calls.register('viewport', (scale) => ({ width: 800 * scale }));
  h.jaspring.calls.register('later', () => Promise.resolve('done'));

  h.deliver({ t: 'call', id: '1', target: 'viewport', args: [2] });
  h.deliver({ t: 'call', id: '2', target: 'later', args: [] });
  await settle();

  assert.deepEqual(h.sentOverSocket(), [
    { t: 'result', page: PAGE, id: '1', value: { width: 1600 } },
    { t: 'result', page: PAGE, id: '2', value: 'done' },
  ]);
});

test('a dotted target runs a method of the hook mounted inside the view', async () => {
  const h = load({ body: HOST });
  h.open();
  h.jaspring.hooks.register('spark', {
    size(el, ctx, unit) { return { width: el.width, height: el.height, unit, page: ctx.page }; },
  });

  h.deliver({ t: 'call', id: '3', target: 'spark.size', v: 1, args: ['px'] });
  await settle();

  assert.deepEqual(h.sentOverSocket()[0], { t: 'result', page: PAGE, id: '3', value: { width: 240, height: 60, unit: 'px', page: PAGE } });
});

test('unknown targets, missing hooks and thrown errors come back as error results', async () => {
  const h = load({ body: HOST });
  h.open();
  h.jaspring.calls.register('explode', () => { throw new Error('kaboom'); });

  h.deliver({ t: 'call', id: '4', target: 'nothing', args: [] });
  h.deliver({ t: 'call', id: '5', target: 'spark.size', v: 1, args: [] });
  h.deliver({ t: 'call', id: '6', target: 'spark.size', v: 9, args: [] });
  h.deliver({ t: 'call', id: '7', target: 'explode', args: [] });
  await settle();

  const sent = h.sentOverSocket();
  assert.equal(sent.length, 4);
  assert.match(sent[0].error.message, /No browser function registered as "nothing"/);
  assert.match(sent[1].error.message, /No mounted hook "spark"/);
  assert.match(sent[2].error.message, /View 9 is not in the document/);
  assert.equal(sent[3].error.message, 'kaboom');
  assert.ok(sent.every((m) => m.t === 'result' && m.id));
});

test('an undefined return value is sent as null', async () => {
  const h = load({ body: HOST });
  h.open();
  h.jaspring.calls.register('noop', () => {});
  h.deliver({ t: 'call', id: '8', target: 'noop', args: [] });
  await settle();
  assert.deepEqual(h.sentOverSocket()[0], { t: 'result', page: PAGE, id: '8', value: null });
});
