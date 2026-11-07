import { test } from 'node:test';
import assert from 'node:assert/strict';
import { load, settle } from './harness.mjs';

const HOST = '<app-x data-jsp-v="1"><button id="b" data-jsp-on="click:1.1">+</button><b id="w">?</b></app-x>';

test('results queue until the socket has joined', async () => {
  const h = load({ body: HOST });
  h.jaspring.calls.register('viewport', () => 1);
  // Deliver through a not-yet-open socket is impossible, so simulate a call answered before join by sending directly.
  h.jaspring.send({ t: 'result', page: 'p1', id: '1', value: 1 });
  assert.equal(h.socket().sent.length, 0, 'nothing goes out before join');

  h.open();
  assert.deepEqual(h.socket().sent.map((m) => m.t), ['join', 'result']);
});

test('the http transport polls at once when the document says messages wait, and keeps polling while pending', async () => {
  const h = load({ body: HOST, transport: 'http', pending: true });
  let remaining = 2;
  h.jaspring.calls.register('viewport', () => ({ width: 640 }));
  h.pollResponse = () => {
    remaining--;
    return remaining === 1
      ? { messages: [{ t: 'call', id: '1', target: 'viewport', args: [] }], pending: true }
      : { messages: [], pending: false };
  };
  await settle(8);

  assert.ok(h.polls >= 2, 'polled while pending: ' + h.polls);
  assert.deepEqual(h.posted[0], { t: 'result', page: 'p1', id: '1', value: { width: 640 } });
});

test('event responses are applied and start polling when the page is still pending', async () => {
  const h = load({ body: HOST, transport: 'http' });
  h.eventResponse = () => ({ messages: [{ t: 'patch', seq: 1, patches: [{ v: 1, html: HOST.replace('?', '320') }] }], pending: true });
  h.pollResponse = () => ({ messages: [], pending: false });
  const pollsBefore = h.polls;

  h.fire(h.document.getElementById('b'), 'click');
  await settle(8);

  assert.equal(h.posted[0].t, 'event');
  assert.equal(h.document.getElementById('w').textContent, '320');
  assert.ok(h.polls > pollsBefore, 'pending response triggered a poll');
});
