/* Jaspring client shim. Sends DOM events to the server, morphs the patches it returns into the page, runs the
   element hooks registered by page scripts, and answers the server's calls into the browser.
   Transports: WebSocket (default, falls back to HTTP when it cannot connect) and HTTP POST with polling.
   Protocol: docs/protocol.md. Markers: data-jsp-v (view host), data-jsp-on (events), data-jsp-model (ngModel),
   data-jsp-hook (client hook, optional data-jsp-hook-params), data-jsp-ignore (morph leaves the element's DOM alone). */
(function () {
  'use strict';

  var script = document.currentScript;
  var pageId = script && script.getAttribute('data-jsp-page');
  var transportName = (script && script.getAttribute('data-jsp-transport')) || 'websocket';
  var pendingAtLoad = !!(script && script.getAttribute('data-jsp-pending'));
  if (!pageId) return;

  var NON_BUBBLING = { focus: true, blur: true, mouseenter: true, mouseleave: true, load: true, error: true, scroll: true };
  var MODIFIERS = { alt: true, control: true, meta: true, shift: true };
  // Matches JaspringEventHandler.MAX_MESSAGE_CHARS.
  var MAX_SOCKET_MESSAGE_CHARS = 4 * 1024 * 1024;
  var listening = {};
  var lastSeq = 0;
  var transport = null;
  // Values sent per ngModel ref. A patch echoing one of them must not touch the input the user is typing in;
  // a value the client never sent means the server changed the model and the input follows it.
  var sentModelValues = {};
  var hooks = {};
  var calls = {};
  // Hooked element -> { name, hook, el, ctx }, for the elements whose hook is registered.
  var mounted = new Map();

  // ---------- event serialization ----------

  function serializeTarget(target) {
    if (!target || target.nodeType !== 1) return null;
    var out = { tagName: target.tagName.toLowerCase() };
    if (target.id) out.id = target.id;
    if (target.name) out.name = target.name;
    if ('value' in target) out.value = readValue(target);
    if (target.type === 'checkbox' || target.type === 'radio') out.checked = target.checked;
    if (target.dataset) out.dataset = Object.assign({}, target.dataset);
    return out;
  }

  function serializeEvent(event) {
    var out = {
      type: event.type,
      target: serializeTarget(event.target),
      currentTarget: serializeTarget(event.currentTarget && event.currentTarget.nodeType === 1 ? event.currentTarget : null)
    };
    ['key', 'code', 'altKey', 'ctrlKey', 'metaKey', 'shiftKey', 'clientX', 'clientY', 'button', 'detail'].forEach(function (name) {
      if (name in event && event[name] !== undefined) out[name] = event[name];
    });
    if (event.type === 'submit' && event.target && event.target.tagName === 'FORM') {
      var form = {};
      new FormData(event.target).forEach(function (value, key) {
        if (key in form) form[key] = [].concat(form[key], value);
        else form[key] = value;
      });
      out.form = form;
    }
    return out;
  }

  // Angular's NumberValueAccessor hands numbers to the model, so number inputs send numbers.
  function readValue(element) {
    if (element.tagName === 'SELECT' && element.multiple) {
      return Array.prototype.map.call(element.selectedOptions, function (o) { return o.value; });
    }
    if (element.type === 'checkbox') return element.checked;
    if (element.type === 'number' || element.type === 'range') {
      return element.value === '' ? null : Number(element.value);
    }
    return element.value;
  }

  // ---------- Angular key event syntax ----------

  function normalizeBinding(spec) {
    var parts = spec.split('.');
    var modifiers = [];
    var key = [];
    for (var i = 1; i < parts.length; i++) {
      if (MODIFIERS[parts[i]] && key.length === 0) modifiers.push(parts[i]);
      else key.push(parts[i]);
    }
    modifiers.sort();
    return { event: parts[0], modifiers: modifiers, key: key.join('.') };
  }

  function eventKey(event, useCode) {
    var key = useCode ? event.code : event.key;
    if (!key) return '';
    key = key.toLowerCase();
    if (key === ' ') key = 'space';
    else if (key === '.') key = 'dot';
    else if (key === 'escape') key = 'esc';
    return key;
  }

  function keyMatches(binding, event) {
    if (!binding.key) return true;
    var useCode = binding.key.indexOf('code.') === 0;
    var wanted = useCode ? binding.key.substring(5) : binding.key;
    if (eventKey(event, useCode) !== wanted) return false;
    var pressed = [];
    if (event.altKey) pressed.push('alt');
    if (event.ctrlKey) pressed.push('control');
    if (event.metaKey) pressed.push('meta');
    if (event.shiftKey) pressed.push('shift');
    return pressed.join('.') === binding.modifiers.join('.');
  }

  // ---------- markers ----------

  function parseBindings(attribute) {
    return attribute.split(/\s+/).filter(Boolean).map(function (entry) {
      var pieces = entry.split(':');
      var target = null;
      var spec = pieces[0];
      var ref = pieces[1];
      var flags = pieces[2] || '';
      if (pieces.length === 4) { target = pieces[0]; spec = pieces[1]; ref = pieces[2]; flags = pieces[3]; }
      else if (pieces.length === 3 && !/^\d+\.\d+$/.test(pieces[1])) { target = pieces[0]; spec = pieces[1]; ref = pieces[2]; flags = ''; }
      var binding = normalizeBinding(spec);
      binding.target = target;
      binding.ref = ref;
      binding.preventDefault = flags.indexOf('pd') >= 0;
      binding.stopPropagation = flags.indexOf('sp') >= 0;
      binding.wasm = flags.split(',').indexOf('w') >= 0;
      return binding;
    });
  }

  function scan() {
    var types = {};
    var wasmHandlers = false;
    document.querySelectorAll('[data-jsp-on]').forEach(function (element) {
      parseBindings(element.getAttribute('data-jsp-on')).forEach(function (binding) {
        var key = (binding.target || '') + ':' + binding.event;
        types[key] = binding;
        if (binding.wasm) wasmHandlers = true;
      });
    });
    if (wasmHandlers && !listening['~warm']) {
      // Hovering or focusing a handler that runs a browser closure starts loading the WebAssembly runtime.
      listening['~warm'] = true;
      document.addEventListener('pointerover', warmOnIntent, { capture: true, passive: true });
      document.addEventListener('focusin', warmOnIntent, { capture: true, passive: true });
    }
    if (document.querySelector('[data-jsp-model]')) {
      types[':input'] = { event: 'input' };
      types[':change'] = { event: 'change' };
    }
    Object.keys(types).forEach(function (key) {
      if (listening[key]) return;
      listening[key] = true;
      var binding = types[key];
      var root = binding.target === 'window' ? window : binding.target === 'document' ? document : document;
      var capture = !!NON_BUBBLING[binding.event];
      if (binding.target === 'window' || binding.target === 'document') {
        root.addEventListener(binding.event, function (event) { dispatchGlobal(binding.target, event); }, capture);
      } else {
        document.addEventListener(binding.event, dispatch, capture);
      }
    });
  }

  function warmOnIntent(event) {
    var wasm = window.jaspring && window.jaspring.wasm;
    if (!wasm || wasm.loaded()) return;
    var node = event.target;
    while (node && node !== document) {
      if (node.nodeType === 1 && node.hasAttribute('data-jsp-on')
          && parseBindings(node.getAttribute('data-jsp-on')).some(function (binding) { return binding.wasm; })) {
        wasm.warm();
        return;
      }
      node = node.parentNode;
    }
  }

  function dispatchGlobal(target, event) {
    document.querySelectorAll('[data-jsp-on]').forEach(function (element) {
      parseBindings(element.getAttribute('data-jsp-on')).forEach(function (binding) {
        if (binding.target !== target || binding.event !== event.type || !keyMatches(binding, event)) return;
        if (binding.preventDefault) event.preventDefault();
        send({ t: 'event', page: pageId, h: binding.ref, ev: serializeEvent(event) });
      });
    });
  }

  function dispatch(event) {
    var node = event.target;
    while (node && node !== document) {
      if (node.nodeType === 1) {
        if (node.hasAttribute('data-jsp-model') && (event.type === 'input' || event.type === 'change')) {
          modelChanged(node, event);
        }
        var attribute = node.getAttribute('data-jsp-on');
        if (attribute) {
          var stop = false;
          parseBindings(attribute).forEach(function (binding) {
            if (binding.target || binding.event !== event.type || !keyMatches(binding, event)) return;
            if (binding.preventDefault || event.type === 'submit') event.preventDefault();
            if (binding.stopPropagation) { event.stopPropagation(); stop = true; }
            send({ t: 'event', page: pageId, h: binding.ref, ev: serializeEvent(event) });
          });
          if (stop) return;
        }
      }
      node = node.parentNode;
    }
  }

  function modelChanged(element, event) {
    var textLike = element.tagName === 'TEXTAREA' || (element.tagName === 'INPUT' && element.type !== 'checkbox' && element.type !== 'radio' && element.type !== 'file');
    if (textLike ? event.type !== 'input' : event.type !== 'change') return;
    var value = element.type === 'radio' ? element.value : readValue(element);
    element.getAttribute('data-jsp-model').split(/\s+/).filter(Boolean).forEach(function (ref) {
      var history = sentModelValues[ref] || (sentModelValues[ref] = []);
      history.push(String(value));
      if (history.length > 50) history.shift();
      send({ t: 'event', page: pageId, h: ref, ev: value });
    });
  }

  function isTextLike(element) {
    return element.tagName === 'TEXTAREA' || (element.tagName === 'INPUT' && element.type !== 'checkbox' && element.type !== 'radio' && element.type !== 'file');
  }

  // The server's rendered state of every bound input in a patch, read from the HTML before morphing: the morph
  // leaves the focused element alone (value attribute included), so the DOM cannot tell us what the server sent.
  function modelStateFrom(html, state) {
    var template = document.createElement('template');
    template.innerHTML = html;
    template.content.querySelectorAll('[data-jsp-model]').forEach(function (element) {
      var entry = { checked: element.hasAttribute('checked') };
      if (element.tagName === 'TEXTAREA') entry.value = element.textContent;
      else if (element.tagName === 'SELECT') entry.selected = Array.prototype.map.call(element.querySelectorAll('option[selected]'), optionValue);
      else entry.value = element.hasAttribute('value') ? element.getAttribute('value') : '';
      element.getAttribute('data-jsp-model').split(/\s+/).filter(Boolean).forEach(function (ref) { state[ref] = entry; });
    });
    return state;
  }

  function optionValue(option) {
    return option.hasAttribute('value') ? option.getAttribute('value') : option.textContent.trim();
  }

  // After the morph, bring bound inputs in line with the server. A value the user typed and the server echoed back
  // is left alone (the user may have typed more since); anything else is a model change and the input follows it.
  function syncModels(state) {
    document.querySelectorAll('[data-jsp-model]').forEach(function (element) {
      var refs = element.getAttribute('data-jsp-model').split(/\s+/).filter(Boolean);
      var entry = state[refs[0]];
      if (!entry) return;
      if (element.tagName === 'SELECT') {
        Array.prototype.forEach.call(element.options, function (option) { option.selected = entry.selected.indexOf(optionValue(option)) >= 0; });
        return;
      }
      if (element.type === 'checkbox' || element.type === 'radio') {
        element.checked = entry.checked;
        return;
      }
      if (!isTextLike(element)) return;
      var history = sentModelValues[refs[0]] || [];
      var echoed = history.indexOf(entry.value);
      if (echoed >= 0) {
        history.splice(0, echoed + 1);
        return;
      }
      if (element.value !== entry.value) element.value = entry.value;
      sentModelValues[refs[0]] = [];
    });
  }

  // ---------- hooks ----------
  // A hook is an object registered with jaspring.hooks.register(name, hook). Its methods receive (el, ctx, ...args):
  // mounted/updated/destroyed follow the element through patches; other methods answer server calls "name.method".

  function readParams(element) {
    var raw = element.getAttribute('data-jsp-hook-params');
    if (raw === null || raw === '') return {};
    try { return JSON.parse(raw); } catch (e) { return raw; }
  }

  function hookContext(element, name) {
    return {
      page: pageId,
      el: element,
      name: name,
      params: readParams(element),
      // A bubbling CustomEvent reaches the component's (type)="..." binding with the detail under $event.detail.
      pushEvent: function (type, detail) {
        element.dispatchEvent(new CustomEvent(type, { bubbles: true, detail: detail }));
      }
    };
  }

  function callHook(entry, method, args) {
    var fn = entry.hook[method];
    if (typeof fn !== 'function') return undefined;
    return fn.apply(entry.hook, [entry.el, entry.ctx].concat(args || []));
  }

  function notifyHook(entry, method) {
    try {
      callHook(entry, method);
    } catch (error) {
      console.error('[jaspring] hook ' + entry.name + '.' + method + ' failed', error);
    }
  }

  function hookedElements(root) {
    var out = [];
    if (root.nodeType === 1 && root.hasAttribute('data-jsp-hook')) out.push(root);
    root.querySelectorAll('[data-jsp-hook]').forEach(function (element) { out.push(element); });
    return out;
  }

  function mountOrUpdate(root) {
    hookedElements(root).forEach(function (element) {
      var entry = mounted.get(element);
      if (entry) {
        entry.ctx.params = readParams(element);
        notifyHook(entry, 'updated');
        return;
      }
      var name = element.getAttribute('data-jsp-hook');
      var hook = hooks[name];
      if (!hook) return;
      entry = { name: name, hook: hook, el: element, ctx: hookContext(element, name) };
      mounted.set(element, entry);
      notifyHook(entry, 'mounted');
    });
  }

  function destroyHook(element) {
    var entry = mounted.get(element);
    if (!entry) return;
    mounted.delete(element);
    notifyHook(entry, 'destroyed');
  }

  // Idiomorph callback: hooks see their element while it is still in the document.
  function destroyIn(node) {
    if (node.nodeType === 1) hookedElements(node).forEach(destroyHook);
  }

  // Idiomorph callback: an element marked data-jsp-ignore keeps its children and the attributes a widget gave it.
  // Only data-* attributes follow the server, so hook params still reach updated (like Phoenix's phx-update="ignore").
  function keepIgnored(oldNode, newNode) {
    // Both sides must carry the marker, so the server can stop ignoring an element by dropping it.
    if (oldNode.nodeType !== 1 || newNode.nodeType !== 1) return true;
    if (!oldNode.hasAttribute('data-jsp-ignore') || !newNode.hasAttribute('data-jsp-ignore')) return true;
    Array.prototype.forEach.call(newNode.attributes, function (attribute) {
      if (attribute.name.indexOf('data-') === 0 && oldNode.getAttribute(attribute.name) !== attribute.value) {
        oldNode.setAttribute(attribute.name, attribute.value);
      }
    });
    return false;
  }

  // Elements that left the document without a removal callback (moved aside by the morph, or replaced wholesale).
  function sweepDetached() {
    mounted.forEach(function (entry, element) {
      if (!element.isConnected) destroyHook(element);
    });
  }

  function registerHook(name, hook) {
    hooks[name] = hook;
    mountOrUpdate(document.documentElement);
  }

  function registerCall(name, fn) {
    calls[name] = fn;
  }

  // ---------- calls from the server ----------

  function resolveCall(message) {
    var target = String(message.target);
    var args = message.args || [];
    var dot = target.indexOf('.');
    if (dot < 0) {
      var fn = calls[target];
      if (typeof fn !== 'function') throw new Error('No browser function registered as "' + target + '"');
      return fn.apply(null, args);
    }
    var hookName = target.substring(0, dot);
    var method = target.substring(dot + 1);
    var host = message.v === undefined || message.v === null ? document.documentElement : document.querySelector('[data-jsp-v="' + message.v + '"]');
    if (!host) throw new Error('View ' + message.v + ' is not in the document');
    var element = host.getAttribute('data-jsp-hook') === hookName ? host : host.querySelector('[data-jsp-hook="' + hookName + '"]');
    var entry = element && mounted.get(element);
    if (!entry) throw new Error('No mounted hook "' + hookName + '" inside view ' + message.v);
    if (typeof entry.hook[method] !== 'function') throw new Error('Hook "' + hookName + '" has no method "' + method + '"');
    return callHook(entry, method, args);
  }

  function invoke(message) {
    var outcome;
    try {
      outcome = Promise.resolve(resolveCall(message));
    } catch (error) {
      outcome = Promise.reject(error);
    }
    outcome.then(function (value) {
      send({ t: 'result', page: pageId, id: message.id, value: value === undefined ? null : value });
    }, function (error) {
      send({ t: 'result', page: pageId, id: message.id, error: { message: error && error.message ? error.message : String(error) } });
    });
  }

  // ---------- patches ----------

  function handle(message) {
    switch (message.t) {
      case 'patch':
        if (message.seq && message.seq <= lastSeq) return;
        if (message.seq) lastSeq = message.seq;
        var modelState = {};
        var patched = [];
        message.patches.forEach(function (patch) {
          var host = document.querySelector('[data-jsp-v="' + patch.v + '"]');
          if (!host) return;
          modelStateFrom(patch.html, modelState);
          if (window.Idiomorph) {
            window.Idiomorph.morph(host, patch.html, { morphStyle: 'outerHTML', ignoreActiveValue: true, callbacks: { beforeNodeRemoved: destroyIn, beforeNodeMorphed: keepIgnored } });
          } else {
            host.outerHTML = patch.html;
          }
          patched.push(patch.v);
        });
        syncModels(modelState);
        scan();
        patched.forEach(function (v) {
          var host = document.querySelector('[data-jsp-v="' + v + '"]');
          if (host) mountOrUpdate(host);
        });
        sweepDetached();
        break;
      case 'call':
        invoke(message);
        break;
      case 'redirect':
        window.location.assign(message.url);
        break;
      case 'error':
        if (message.code === 'unknown-page') { window.location.reload(); return; }
        console.error('[jaspring] ' + message.code + ': ' + message.message);
        break;
      case 'ping':
        send({ t: 'pong' });
        break;
    }
  }

  function send(message) {
    transport.send(message);
  }

  // ---------- WebSocket transport ----------

  function webSocketTransport() {
    var socket = null;
    var queue = [];
    var everOpened = false;
    var joined = false;
    var attempts = 0;
    var api = {
      send: function (message) {
        if (!(socket && socket.readyState === WebSocket.OPEN && joined)) {
          if (message.t !== 'pong') queue.push(message);
          return;
        }
        var json = encodeForSocket(message);
        if (json !== null) socket.send(json);
      }
    };

    // The server closes the socket on a message above its limit, so an oversized result fails its call instead.
    function encodeForSocket(message) {
      var json = JSON.stringify(message);
      if (json.length <= MAX_SOCKET_MESSAGE_CHARS) return json;
      if (message.t === 'result') {
        return JSON.stringify({ t: 'result', page: pageId, id: message.id, error: {
          message: 'Result is ' + json.length + ' characters; the WebSocket limit is ' + MAX_SOCKET_MESSAGE_CHARS } });
      }
      console.error('[jaspring] dropped a ' + message.t + ' message of ' + json.length + ' characters, above the WebSocket limit');
      return null;
    }

    function connect() {
      var url = (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/_jaspring/ws';
      socket = new WebSocket(url);
      socket.onopen = function () {
        everOpened = true;
        attempts = 0;
        socket.send(JSON.stringify({ t: 'join', page: pageId }));
        joined = true;
        var pending = queue.splice(0);
        pending.forEach(api.send);
      };
      socket.onmessage = function (event) { handle(JSON.parse(event.data)); };
      socket.onclose = function () {
        joined = false;
        if (!everOpened && transportName === 'websocket') {
          switchToHttp(queue.splice(0));
          return;
        }
        var delay = Math.min(10000, 500 * Math.pow(2, attempts++)) * (0.8 + Math.random() * 0.4);
        setTimeout(connect, delay);
      };
      socket.onerror = function () { /* onclose follows */ };
    }

    connect();
    return api;
  }

  // ---------- HTTP transport ----------

  function httpTransport() {
    var queue = [];
    var inFlight = false;
    var polling = false;
    var api = {
      send: function (message) {
        if (message.t === 'pong') return;
        queue.push(message);
        pump();
      }
    };

    function headers() {
      var out = { 'Content-Type': 'application/json' };
      var token = document.querySelector('meta[name="_csrf"]');
      var header = document.querySelector('meta[name="_csrf_header"]');
      if (token && header) out[header.getAttribute('content')] = token.getAttribute('content');
      return out;
    }

    function pump() {
      if (inFlight || queue.length === 0) return;
      inFlight = true;
      var message = queue.shift();
      fetch('/_jaspring/event', { method: 'POST', headers: headers(), body: JSON.stringify(message), credentials: 'same-origin' })
        .then(response)
        .catch(function (error) { console.error('[jaspring] transport error', error); })
        .then(function () { inFlight = false; pump(); });
    }

    function response(res) {
      if (res.status === 410) { window.location.reload(); return; }
      return res.json().then(function (body) {
        (body.messages || []).forEach(handle);
        if (body.pending && !polling) poll();
      });
    }

    function poll(immediately) {
      polling = true;
      setTimeout(function () {
        fetch('/_jaspring/poll?page=' + encodeURIComponent(pageId), { credentials: 'same-origin' })
          .then(function (res) {
            if (res.status === 410) { window.location.reload(); return { messages: [], pending: false }; }
            return res.json();
          })
          .then(function (body) {
            (body.messages || []).forEach(handle);
            polling = false;
            if (body.pending) poll();
          })
          .catch(function () { polling = false; });
      }, immediately ? 0 : 500);
    }

    // Work queued while the socket was failing is not lost; forked results and calls made while rendering are
    // fetched by polling once, at once when the server said messages already wait.
    api.resume = function (pending) {
      pending.forEach(function (message) { if (message.t === 'event' || message.t === 'result') queue.push(message); });
      pump();
      if (!polling) poll(pendingAtLoad);
    };
    return api;
  }

  function switchToHttp(pending) {
    transportName = 'http';
    transport = httpTransport();
    transport.resume(pending || []);
  }

  transport = transportName === 'http' ? httpTransport() : webSocketTransport();
  if (transportName === 'http') transport.resume([]);
  scan();
  window.jaspring = {
    page: pageId,
    send: send,
    transport: function () { return transportName; },
    hooks: { register: registerHook },
    calls: { register: registerCall }
  };
  mountOrUpdate(document.documentElement);
})();
