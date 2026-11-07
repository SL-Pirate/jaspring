import { test } from 'node:test';
import assert from 'node:assert/strict';
import { load, settle } from './harness.mjs';

function recorder() {
  const log = [];
  const hook = {
    mounted(el, ctx) { log.push('mounted:' + el.id + ':' + JSON.stringify(ctx.params)); },
    updated(el, ctx) { log.push('updated:' + el.id + ':' + JSON.stringify(ctx.params)); },
    destroyed(el) { log.push('destroyed:' + el.id); },
  };
  return { log, hook };
}

const HOST = (inner) => '<app-x data-jsp-v="1">' + inner + '</app-x>';

test('mounted runs for hooked elements present at load, whether the hook registers before or after the shim', async () => {
  const h = load({ body: HOST('<canvas id="a" data-jsp-hook="spark" data-jsp-hook-params=\'{"n":2}\'></canvas>') });
  const { log, hook } = recorder();
  h.jaspring.hooks.register('spark', hook);
  assert.deepEqual(log, ['mounted:a:{"n":2}']);
  h.jaspring.hooks.register('spark', hook);
  assert.deepEqual(log, ['mounted:a:{"n":2}', 'updated:a:{"n":2}'], 're-registering does not mount twice');
});

test('a patch that adds a hooked element mounts it, one that morphs it updates it, one that removes it destroys it', async () => {
  const h = load({ body: HOST('<p id="p">none</p>') });
  h.open();
  const { log, hook } = recorder();
  h.jaspring.hooks.register('spark', hook);

  h.deliver({ t: 'patch', seq: 1, patches: [{ v: 1, html: HOST('<p id="p">one</p><canvas id="a" data-jsp-hook="spark" data-jsp-hook-params="[1]"></canvas>') }] });
  assert.deepEqual(log, ['mounted:a:[1]']);
  const canvas = h.document.getElementById('a');

  h.deliver({ t: 'patch', seq: 2, patches: [{ v: 1, html: HOST('<p id="p">two</p><canvas id="a" data-jsp-hook="spark" data-jsp-hook-params="[1,2]"></canvas>') }] });
  assert.deepEqual(log, ['mounted:a:[1]', 'updated:a:[1,2]']);
  assert.equal(h.document.getElementById('a'), canvas, 'the morph kept the element');

  h.deliver({ t: 'patch', seq: 3, patches: [{ v: 1, html: HOST('<p id="p">three</p>') }] });
  assert.deepEqual(log, ['mounted:a:[1]', 'updated:a:[1,2]', 'destroyed:a']);
  assert.equal(h.document.getElementById('a'), null);
});

test('elements that leave the document without a removal callback are still destroyed', async () => {
  // Without Idiomorph the host is replaced wholesale, so no beforeNodeRemoved fires.
  const h = load({ body: HOST('<div id="w" data-jsp-hook="widget"></div>'), idiomorph: false });
  h.open();
  const { log, hook } = recorder();
  h.jaspring.hooks.register('widget', hook);

  h.deliver({ t: 'patch', seq: 1, patches: [{ v: 1, html: HOST('<span>gone</span>') }] });
  assert.deepEqual(log, ['mounted:w:{}', 'destroyed:w']);
});

test('pushEvent dispatches a bubbling custom event that reaches the component binding', async () => {
  const h = load({ body: HOST('<canvas id="a" data-jsp-hook="spark" data-jsp-on="point-picked:1.1"></canvas>') });
  h.open();
  let context;
  h.jaspring.hooks.register('spark', { mounted(el, ctx) { context = ctx; } });
  context.pushEvent('point-picked', { index: 4 });
  await settle();

  const [event] = h.sentOverSocket();
  assert.equal(event.h, '1.1');
  assert.deepEqual(event.ev.detail, { index: 4 });
  assert.equal(context.page, 'p1');
  assert.equal(context.name, 'spark');
});

test('a failing hook method is logged and does not break the patch', async () => {
  const h = load({ body: HOST('<p>x</p>') });
  h.open();
  h.jaspring.hooks.register('bad', { mounted() { throw new Error('boom'); } });

  h.deliver({ t: 'patch', seq: 1, patches: [{ v: 1, html: HOST('<p>y</p><i id="i" data-jsp-hook="bad"></i>') }] });
  assert.equal(h.document.querySelector('p').textContent, 'y');
  assert.equal(h.errors.length, 1);
  assert.match(h.errors[0], /hook bad\.mounted failed/);
});

test('data-jsp-ignore keeps widget DOM and attributes through a patch, follows data attributes and still updates', async () => {
  const h = load({ body: HOST('<p id="p">one</p><div id="w" data-jsp-hook="widget" data-jsp-ignore data-jsp-hook-params="[1]"></div>') });
  h.open();
  const { log, hook } = recorder();
  h.jaspring.hooks.register('widget', hook);
  const widget = h.document.getElementById('w');
  widget.innerHTML = '<canvas id="built"></canvas>';
  widget.setAttribute('style', 'width: 240px');

  h.deliver({ t: 'patch', seq: 1, patches: [{ v: 1, html: HOST('<p id="p">two</p><div id="w" data-jsp-hook="widget" data-jsp-ignore data-jsp-hook-params="[1,2]">server</div>') }] });

  assert.equal(h.document.getElementById('p').textContent, 'two', 'the rest of the view is morphed');
  assert.equal(h.document.getElementById('w'), widget);
  assert.ok(h.document.getElementById('built'), 'children the widget built are kept');
  assert.equal(widget.getAttribute('style'), 'width: 240px', 'attributes the widget set are kept');
  assert.deepEqual(log, ['mounted:w:[1]', 'updated:w:[1,2]']);
});

test('dropping data-jsp-ignore lets the next patch morph the element again', async () => {
  const h = load({ body: HOST('<div id="w" data-jsp-ignore>old</div>') });
  h.open();

  h.deliver({ t: 'patch', seq: 1, patches: [{ v: 1, html: HOST('<div id="w">new</div>') }] });
  assert.equal(h.document.getElementById('w').textContent, 'new');
});
