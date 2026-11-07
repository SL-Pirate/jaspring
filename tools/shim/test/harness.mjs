// Loads the shipped idiomorph.min.js and jaspring.js into a jsdom window with fake transports.
// The shim under test is always the file in jaspring-spring-boot-starter, never a copy.
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { JSDOM } from 'jsdom';

const here = path.dirname(fileURLToPath(import.meta.url));
const staticDir = path.resolve(here, '../../../jaspring-spring-boot-starter/src/main/resources/jaspring/static');
const IDIOMORPH = readFileSync(path.join(staticDir, 'idiomorph.min.js'), 'utf8');
const SHIM = readFileSync(path.join(staticDir, 'jaspring.js'), 'utf8');

export const PAGE = 'p1';

/// Waits for queued promises, fake fetches and (shortened) timers to run.
export function settle(rounds = 4) {
  let chain = Promise.resolve();
  for (let i = 0; i < rounds; i++) chain = chain.then(() => new Promise((resolve) => setTimeout(resolve, 2)));
  return chain;
}

export function load({ body = '', transport = 'websocket', pending = false, idiomorph = true } = {}) {
  const dom = new JSDOM(`<!doctype html><html><head></head><body>${body}</body></html>`,
    { url: 'http://localhost/', runScripts: 'outside-only', pretendToBeVisual: true });
  const { window } = dom;
  const document = window.document;

  // Timers: every delay becomes immediate so backoff and polling do not slow the tests down.
  const realSetTimeout = window.setTimeout.bind(window);
  window.setTimeout = (fn, _delay, ...args) => realSetTimeout(fn, 0, ...args);

  const harness = {
    window,
    document,
    sockets: [],
    posted: [],      // bodies POSTed to /_jaspring/event (http transport)
    polls: 0,
    eventResponse: () => ({ messages: [], pending: false }),
    pollResponse: () => ({ messages: [], pending: false }),
    errors: [],
  };

  window.console.error = (...args) => harness.errors.push(args.map(String).join(' '));

  class FakeWebSocket {
    static CONNECTING = 0;
    static OPEN = 1;
    static CLOSED = 3;
    constructor(url) {
      this.url = url;
      this.readyState = FakeWebSocket.CONNECTING;
      this.sent = [];
      harness.sockets.push(this);
    }
    send(data) { this.sent.push(JSON.parse(data)); }
    close() { this.readyState = FakeWebSocket.CLOSED; this.onclose && this.onclose({}); }
  }
  window.WebSocket = FakeWebSocket;

  window.fetch = (url, init = {}) => {
    const target = String(url);
    let body;
    if (target.startsWith('/_jaspring/event')) {
      const message = JSON.parse(init.body);
      harness.posted.push(message);
      body = harness.eventResponse(message);
    } else if (target.startsWith('/_jaspring/poll')) {
      harness.polls++;
      body = harness.pollResponse();
    } else {
      return Promise.reject(new Error('unexpected fetch ' + target));
    }
    return Promise.resolve({ status: 200, json: () => Promise.resolve(body) });
  };

  if (idiomorph) window.eval(IDIOMORPH);
  const script = document.createElement('script');
  script.setAttribute('data-jsp-page', PAGE);
  script.setAttribute('data-jsp-transport', transport);
  if (pending) script.setAttribute('data-jsp-pending', 'true');
  Object.defineProperty(document, 'currentScript', { value: script, configurable: true });
  window.eval(SHIM);

  harness.jaspring = window.jaspring;
  harness.socket = () => harness.sockets[harness.sockets.length - 1];
  /// Opens the latest fake socket; the shim then joins and flushes its queue.
  harness.open = () => {
    const socket = harness.socket();
    socket.readyState = FakeWebSocket.OPEN;
    socket.onopen();
    return socket;
  };
  /// Delivers a server message through the open socket.
  harness.deliver = (message) => harness.socket().onmessage({ data: JSON.stringify(message) });
  /// Messages the shim sent over the socket, excluding the join.
  harness.sentOverSocket = () => harness.socket().sent.filter((m) => m.t !== 'join');
  harness.fire = (element, type, init = {}) => element.dispatchEvent(new window.Event(type, { bubbles: true, cancelable: true, ...init }));
  return harness;
}
