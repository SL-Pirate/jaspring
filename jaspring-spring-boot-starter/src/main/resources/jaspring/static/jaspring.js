/* Jaspring client shim. Sends DOM events to the server and morphs the patches it returns into the page.
   Transports: WebSocket (default, falls back to HTTP when it cannot connect) and HTTP POST with polling.
   Protocol: docs/protocol.md. Markers: data-jsp-v (view host), data-jsp-on (events), data-jsp-model (ngModel). */
(function () {
  'use strict';

  var script = document.currentScript;
  var pageId = script && script.getAttribute('data-jsp-page');
  var transportName = (script && script.getAttribute('data-jsp-transport')) || 'websocket';
  if (!pageId) return;

  var NON_BUBBLING = { focus: true, blur: true, mouseenter: true, mouseleave: true, load: true, error: true, scroll: true };
  var MODIFIERS = { alt: true, control: true, meta: true, shift: true };
  var listening = {};
  var lastSeq = 0;
  var transport = null;
  // Values sent per ngModel ref. A patch echoing one of them must not touch the input the user is typing in;
  // a value the client never sent means the server changed the model and the input follows it.
  var sentModelValues = {};

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
      return binding;
    });
  }

  function scan() {
    var types = {};
    document.querySelectorAll('[data-jsp-on]').forEach(function (element) {
      parseBindings(element.getAttribute('data-jsp-on')).forEach(function (binding) {
        var key = (binding.target || '') + ':' + binding.event;
        types[key] = binding;
      });
    });
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

  // ---------- patches ----------

  function handle(message) {
    switch (message.t) {
      case 'patch':
        if (message.seq && message.seq <= lastSeq) return;
        if (message.seq) lastSeq = message.seq;
        var modelState = {};
        message.patches.forEach(function (patch) {
          var host = document.querySelector('[data-jsp-v="' + patch.v + '"]');
          if (!host) return;
          modelStateFrom(patch.html, modelState);
          if (window.Idiomorph) {
            window.Idiomorph.morph(host, patch.html, { morphStyle: 'outerHTML', ignoreActiveValue: true });
          } else {
            host.outerHTML = patch.html;
          }
        });
        syncModels(modelState);
        scan();
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
        if (socket && socket.readyState === WebSocket.OPEN && joined) socket.send(JSON.stringify(message));
        else if (message.t !== 'pong') queue.push(message);
      }
    };

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

    function poll() {
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
      }, 500);
    }

    // Work queued while the socket was failing is not lost; forked results are fetched by polling once.
    api.resume = function (pending) {
      pending.forEach(function (message) { if (message.t === 'event') queue.push(message); });
      pump();
      if (!polling) poll();
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
  window.jaspring = { page: pageId, send: send, transport: function () { return transportName; } };
})();
