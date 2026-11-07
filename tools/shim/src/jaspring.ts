/* Jaspring client shim. Sends DOM events to the server, morphs the patches it returns into the page, runs the
   element hooks registered by page scripts, and answers the server's calls into the browser.
   Transports: WebSocket (default, falls back to HTTP when it cannot connect) and HTTP POST with polling.
   Protocol: docs/protocol.md. Markers: data-jsp-v (view host), data-jsp-on (events), data-jsp-model (ngModel),
   data-jsp-hook (client hook, optional data-jsp-hook-params), data-jsp-ignore (morph leaves the element's DOM alone).
   Source: tools/shim/src/jaspring.ts; this file is generated, do not edit. */
(function () {
  "use strict";

  const script = document.currentScript;
  const pageId = (script && script.getAttribute("data-jsp-page")) || "";
  let transportName: "websocket" | "http" =
    script && script.getAttribute("data-jsp-transport") === "http"
      ? "http"
      : "websocket";
  const pendingAtLoad = !!(script && script.getAttribute("data-jsp-pending"));
  if (!pageId) return;

  // ---------- messages (docs/protocol.md) ----------

  type EventMessage = { t: "event"; page: string; h: string; ev: unknown };
  type ResultMessage = {
    t: "result";
    page: string;
    id: unknown;
    value?: unknown;
    error?: { message: string };
  };
  type ClientMessage =
    EventMessage | ResultMessage | { t: "pong" } | { t: "join"; page: string };

  type Patch = { v: string; html: string };
  type CallMessage = {
    t: "call";
    id: unknown;
    target: string;
    args?: unknown[];
    v?: string | number | null;
  };
  type ServerMessage =
    | { t: "patch"; seq?: number; patches: Patch[] }
    | CallMessage
    | { t: "redirect"; url: string }
    | { t: "error"; code: string; message: string }
    | { t: "ping" };

  interface Transport {
    send(message: ClientMessage): void;
  }

  interface ResumableTransport extends Transport {
    resume(pending: ClientMessage[]): void;
  }

  // ---------- state ----------

  type FormControl = HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement;
  type ModelValue = string | number | boolean | string[] | null;
  type ModelEntry = { checked: boolean; value?: string; selected?: string[] };
  type ModelState = Record<string, ModelEntry>;
  type SerializedTarget = {
    tagName: string;
    id?: string;
    name?: string;
    value?: ModelValue;
    checked?: boolean;
    dataset?: Record<string, string | undefined>;
  };
  type Binding = {
    event: string;
    modifiers: string[];
    key: string;
    target: string | null;
    ref: string;
    preventDefault: boolean;
    stopPropagation: boolean;
    wasm: boolean;
  };
  type HookEntry = {
    name: string;
    hook: JaspringHook<any, any>;
    el: Element;
    ctx: JaspringHookContext<unknown>;
  };

  const NON_BUBBLING: Record<string, boolean> = {
    focus: true,
    blur: true,
    mouseenter: true,
    mouseleave: true,
    load: true,
    error: true,
    scroll: true,
  };
  const MODIFIERS: Record<string, boolean> = {
    alt: true,
    control: true,
    meta: true,
    shift: true,
  };
  // Matches JaspringEventHandler.MAX_MESSAGE_CHARS.
  const MAX_SOCKET_MESSAGE_CHARS = 4 * 1024 * 1024;
  const listening: Record<string, boolean> = {};
  let lastSeq = 0;
  let transport: Transport;
  // Values sent per ngModel ref. A patch echoing one of them must not touch the input the user is typing in;
  // a value the client never sent means the server changed the model and the input follows it.
  const sentModelValues: Record<string, string[]> = {};
  const hooks: Record<string, JaspringHook<any, any>> = {};
  const calls: Record<string, JaspringCallFn> = {};
  // Hooked element -> { name, hook, el, ctx }, for the elements whose hook is registered.
  const mounted = new Map<Element, HookEntry>();

  // ---------- event serialization ----------

  function serializeTarget(
    target: EventTarget | null,
  ): SerializedTarget | null {
    if (!target || (target as Node).nodeType !== 1) return null;
    const element = target as FormControl;
    const out: SerializedTarget = { tagName: element.tagName.toLowerCase() };
    if (element.id) out.id = element.id;
    if (element.name) out.name = element.name;
    if ("value" in element) out.value = readValue(element);
    if (element.type === "checkbox" || element.type === "radio")
      out.checked = (element as HTMLInputElement).checked;
    if (element.dataset) out.dataset = Object.assign({}, element.dataset);
    return out;
  }

  function serializeEvent(event: Event): Record<string, unknown> {
    const currentTarget =
      event.currentTarget && (event.currentTarget as Node).nodeType === 1
        ? event.currentTarget
        : null;
    const out: Record<string, unknown> = {
      type: event.type,
      target: serializeTarget(event.target),
      currentTarget: serializeTarget(currentTarget),
    };
    const details = event as unknown as Record<string, unknown>;
    [
      "key",
      "code",
      "altKey",
      "ctrlKey",
      "metaKey",
      "shiftKey",
      "clientX",
      "clientY",
      "button",
      "detail",
    ].forEach(function (name) {
      if (name in event && details[name] !== undefined)
        out[name] = details[name];
    });
    if (
      event.type === "submit" &&
      event.target &&
      (event.target as Element).tagName === "FORM"
    ) {
      const form: Record<string, unknown> = {};
      new FormData(event.target as HTMLFormElement).forEach(
        function (value, key) {
          if (key in form)
            form[key] = Array.isArray(form[key])
              ? [...(form[key] as unknown[]), value]
              : [form[key], value];
          else form[key] = value;
        },
      );
      out.form = form;
    }
    return out;
  }

  // Angular's NumberValueAccessor hands numbers to the model, so number inputs send numbers.
  function readValue(element: FormControl): ModelValue {
    if (
      element.tagName === "SELECT" &&
      (element as HTMLSelectElement).multiple
    ) {
      return Array.from(
        (element as HTMLSelectElement).selectedOptions,
        function (option) {
          return option.value;
        },
      );
    }
    if (element.type === "checkbox")
      return (element as HTMLInputElement).checked;
    if (element.type === "number" || element.type === "range") {
      return element.value === "" ? null : Number(element.value);
    }
    return element.value;
  }

  // ---------- Angular key event syntax ----------

  function normalizeBinding(spec: string): {
    event: string;
    modifiers: string[];
    key: string;
  } {
    const parts = spec.split(".");
    const modifiers: string[] = [];
    const key: string[] = [];
    for (let i = 1; i < parts.length; i++) {
      if (MODIFIERS[parts[i]] && key.length === 0) modifiers.push(parts[i]);
      else key.push(parts[i]);
    }
    modifiers.sort();
    return { event: parts[0], modifiers: modifiers, key: key.join(".") };
  }

  function eventKey(event: Event, useCode: boolean): string {
    let key = useCode
      ? (event as KeyboardEvent).code
      : (event as KeyboardEvent).key;
    if (!key) return "";
    key = key.toLowerCase();
    if (key === " ") key = "space";
    else if (key === ".") key = "dot";
    else if (key === "escape") key = "esc";
    return key;
  }

  function keyMatches(binding: Binding, event: Event): boolean {
    if (!binding.key) return true;
    const useCode = binding.key.indexOf("code.") === 0;
    const wanted = useCode ? binding.key.substring(5) : binding.key;
    if (eventKey(event, useCode) !== wanted) return false;
    const keys = event as KeyboardEvent;
    const pressed: string[] = [];
    if (keys.altKey) pressed.push("alt");
    if (keys.ctrlKey) pressed.push("control");
    if (keys.metaKey) pressed.push("meta");
    if (keys.shiftKey) pressed.push("shift");
    return pressed.join(".") === binding.modifiers.join(".");
  }

  // ---------- markers ----------

  function parseBindings(attribute: string): Binding[] {
    return attribute
      .split(/\s+/)
      .filter(Boolean)
      .map(function (entry) {
        const pieces = entry.split(":");
        let target: string | null = null;
        let spec = pieces[0];
        let ref = pieces[1];
        let flags = pieces[2] || "";
        if (pieces.length === 4) {
          target = pieces[0];
          spec = pieces[1];
          ref = pieces[2];
          flags = pieces[3];
        } else if (pieces.length === 3 && !/^\d+\.\d+$/.test(pieces[1])) {
          target = pieces[0];
          spec = pieces[1];
          ref = pieces[2];
          flags = "";
        }
        const binding = normalizeBinding(spec);
        return {
          event: binding.event,
          modifiers: binding.modifiers,
          key: binding.key,
          target: target,
          ref: ref,
          preventDefault: flags.indexOf("pd") >= 0,
          stopPropagation: flags.indexOf("sp") >= 0,
          wasm: flags.split(",").indexOf("w") >= 0,
        };
      });
  }

  function scan(): void {
    const types: Record<
      string,
      { event: string; target: string | null; wasm: boolean }
    > = {};
    let wasmHandlers = false;
    document.querySelectorAll("[data-jsp-on]").forEach(function (element) {
      parseBindings(element.getAttribute("data-jsp-on") || "").forEach(
        function (binding) {
          const key = (binding.target || "") + ":" + binding.event;
          types[key] = binding;
          if (binding.wasm) wasmHandlers = true;
        },
      );
    });
    if (wasmHandlers && !listening["~warm"]) {
      // Hovering or focusing a handler that runs a browser closure starts loading the WebAssembly runtime.
      listening["~warm"] = true;
      document.addEventListener("pointerover", warmOnIntent, {
        capture: true,
        passive: true,
      });
      document.addEventListener("focusin", warmOnIntent, {
        capture: true,
        passive: true,
      });
    }
    if (document.querySelector("[data-jsp-model]")) {
      types[":input"] = { event: "input", target: null, wasm: false };
      types[":change"] = { event: "change", target: null, wasm: false };
    }
    Object.keys(types).forEach(function (key) {
      if (listening[key]) return;
      listening[key] = true;
      const binding = types[key];
      const capture = !!NON_BUBBLING[binding.event];
      if (binding.target === "window" || binding.target === "document") {
        const root: EventTarget =
          binding.target === "window" ? window : document;
        const target = binding.target;
        root.addEventListener(
          binding.event,
          function (event) {
            dispatchGlobal(target, event);
          },
          capture,
        );
      } else {
        document.addEventListener(binding.event, dispatch, capture);
      }
    });
  }

  function warmOnIntent(event: Event): void {
    const wasm = window.jaspring && window.jaspring.wasm;
    if (!wasm || wasm.loaded()) return;
    let node = event.target as Node | null;
    while (node && node !== document) {
      if (
        node.nodeType === 1 &&
        (node as Element).hasAttribute("data-jsp-on") &&
        parseBindings((node as Element).getAttribute("data-jsp-on") || "").some(
          function (binding) {
            return binding.wasm;
          },
        )
      ) {
        wasm.warm();
        return;
      }
      node = node.parentNode;
    }
  }

  function dispatchGlobal(target: string, event: Event): void {
    document.querySelectorAll("[data-jsp-on]").forEach(function (element) {
      parseBindings(element.getAttribute("data-jsp-on") || "").forEach(
        function (binding) {
          if (
            binding.target !== target ||
            binding.event !== event.type ||
            !keyMatches(binding, event)
          )
            return;
          if (binding.preventDefault) event.preventDefault();
          send({
            t: "event",
            page: pageId,
            h: binding.ref,
            ev: serializeEvent(event),
          });
        },
      );
    });
  }

  function dispatch(event: Event): void {
    let node = event.target as Node | null;
    while (node && node !== document) {
      if (node.nodeType === 1) {
        const element = node as Element;
        if (
          element.hasAttribute("data-jsp-model") &&
          (event.type === "input" || event.type === "change")
        ) {
          modelChanged(element as FormControl, event);
        }
        const attribute = element.getAttribute("data-jsp-on");
        if (attribute) {
          let stop = false;
          parseBindings(attribute).forEach(function (binding) {
            if (
              binding.target ||
              binding.event !== event.type ||
              !keyMatches(binding, event)
            )
              return;
            if (binding.preventDefault || event.type === "submit")
              event.preventDefault();
            if (binding.stopPropagation) {
              event.stopPropagation();
              stop = true;
            }
            send({
              t: "event",
              page: pageId,
              h: binding.ref,
              ev: serializeEvent(event),
            });
          });
          if (stop) return;
        }
      }
      node = node.parentNode;
    }
  }

  function modelChanged(element: FormControl, event: Event): void {
    if (isTextLike(element) ? event.type !== "input" : event.type !== "change")
      return;
    const value = element.type === "radio" ? element.value : readValue(element);
    (element.getAttribute("data-jsp-model") || "")
      .split(/\s+/)
      .filter(Boolean)
      .forEach(function (ref) {
        const history = sentModelValues[ref] || (sentModelValues[ref] = []);
        history.push(String(value));
        if (history.length > 50) history.shift();
        send({ t: "event", page: pageId, h: ref, ev: value });
      });
  }

  function isTextLike(element: Element): boolean {
    const type = (element as HTMLInputElement).type;
    return (
      element.tagName === "TEXTAREA" ||
      (element.tagName === "INPUT" &&
        type !== "checkbox" &&
        type !== "radio" &&
        type !== "file")
    );
  }

  // The server's rendered state of every bound input in a patch, read from the HTML before morphing: the morph
  // leaves the focused element alone (value attribute included), so the DOM cannot tell us what the server sent.
  function modelStateFrom(html: string, state: ModelState): ModelState {
    const template = document.createElement("template");
    template.innerHTML = html;
    template.content
      .querySelectorAll("[data-jsp-model]")
      .forEach(function (element) {
        const entry: ModelEntry = { checked: element.hasAttribute("checked") };
        if (element.tagName === "TEXTAREA")
          entry.value = element.textContent || "";
        else if (element.tagName === "SELECT")
          entry.selected = Array.from(
            element.querySelectorAll("option[selected]"),
            optionValue,
          );
        else
          entry.value = element.hasAttribute("value")
            ? element.getAttribute("value") || ""
            : "";
        (element.getAttribute("data-jsp-model") || "")
          .split(/\s+/)
          .filter(Boolean)
          .forEach(function (ref) {
            state[ref] = entry;
          });
      });
    return state;
  }

  function optionValue(option: Element): string {
    return option.hasAttribute("value")
      ? option.getAttribute("value") || ""
      : (option.textContent || "").trim();
  }

  // After the morph, bring bound inputs in line with the server. A value the user typed and the server echoed back
  // is left alone (the user may have typed more since); anything else is a model change and the input follows it.
  function syncModels(state: ModelState): void {
    document.querySelectorAll("[data-jsp-model]").forEach(function (element) {
      const refs = (element.getAttribute("data-jsp-model") || "")
        .split(/\s+/)
        .filter(Boolean);
      const entry = state[refs[0]];
      if (!entry) return;
      if (element.tagName === "SELECT") {
        const selected = entry.selected || [];
        Array.from((element as HTMLSelectElement).options).forEach(
          function (option) {
            option.selected = selected.indexOf(optionValue(option)) >= 0;
          },
        );
        return;
      }
      const input = element as HTMLInputElement;
      if (input.type === "checkbox" || input.type === "radio") {
        input.checked = entry.checked;
        return;
      }
      if (!isTextLike(element)) return;
      const value = entry.value || "";
      const history = sentModelValues[refs[0]] || [];
      const echoed = history.indexOf(value);
      if (echoed >= 0) {
        history.splice(0, echoed + 1);
        return;
      }
      if (input.value !== value) input.value = value;
      sentModelValues[refs[0]] = [];
    });
  }

  // ---------- hooks ----------
  // A hook is an object registered with jaspring.hooks.register(name, hook). Its methods receive (el, ctx, ...args):
  // mounted/updated/destroyed follow the element through patches; other methods answer server calls "name.method".

  function readParams(element: Element): unknown {
    const raw = element.getAttribute("data-jsp-hook-params");
    if (raw === null || raw === "") return {};
    try {
      return JSON.parse(raw);
    } catch (e) {
      return raw;
    }
  }

  function hookContext(
    element: Element,
    name: string,
  ): JaspringHookContext<unknown> {
    return {
      page: pageId,
      el: element as HTMLElement,
      name: name,
      params: readParams(element),
      // A bubbling CustomEvent reaches the component's (type)="..." binding with the detail under $event.detail.
      pushEvent: function (type, detail) {
        element.dispatchEvent(
          new CustomEvent(type, { bubbles: true, detail: detail }),
        );
      },
    };
  }

  function callHook(
    entry: HookEntry,
    method: string,
    args?: unknown[],
  ): unknown {
    const fn = entry.hook[method];
    if (typeof fn !== "function") return undefined;
    return fn.call(entry.hook, entry.el, entry.ctx, ...(args || []));
  }

  function notifyHook(entry: HookEntry, method: string): void {
    try {
      callHook(entry, method);
    } catch (error) {
      console.error(
        "[jaspring] hook " + entry.name + "." + method + " failed",
        error,
      );
    }
  }

  function hookedElements(root: Node): Element[] {
    const out: Element[] = [];
    if (root.nodeType === 1 && (root as Element).hasAttribute("data-jsp-hook"))
      out.push(root as Element);
    (root as Element)
      .querySelectorAll("[data-jsp-hook]")
      .forEach(function (element) {
        out.push(element);
      });
    return out;
  }

  function mountOrUpdate(root: Element): void {
    hookedElements(root).forEach(function (element) {
      const existing = mounted.get(element);
      if (existing) {
        existing.ctx.params = readParams(element);
        notifyHook(existing, "updated");
        return;
      }
      const name = element.getAttribute("data-jsp-hook") || "";
      const hook = hooks[name];
      if (!hook) return;
      const entry: HookEntry = {
        name: name,
        hook: hook,
        el: element,
        ctx: hookContext(element, name),
      };
      mounted.set(element, entry);
      notifyHook(entry, "mounted");
    });
  }

  function destroyHook(element: Element): void {
    const entry = mounted.get(element);
    if (!entry) return;
    mounted.delete(element);
    notifyHook(entry, "destroyed");
  }

  // Idiomorph callback: hooks see their element while it is still in the document.
  function destroyIn(node: Node): void {
    if (node.nodeType === 1) hookedElements(node).forEach(destroyHook);
  }

  // Idiomorph callback: an element marked data-jsp-ignore keeps its children and the attributes a widget gave it.
  // Only data-* attributes follow the server, so hook params still reach updated (like Phoenix's phx-update="ignore").
  function keepIgnored(oldNode: Node, newNode: Node): boolean {
    // Both sides must carry the marker, so the server can stop ignoring an element by dropping it.
    if (oldNode.nodeType !== 1 || newNode.nodeType !== 1) return true;
    const oldElement = oldNode as Element;
    const newElement = newNode as Element;
    if (
      !oldElement.hasAttribute("data-jsp-ignore") ||
      !newElement.hasAttribute("data-jsp-ignore")
    )
      return true;
    Array.from(newElement.attributes).forEach(function (attribute) {
      if (
        attribute.name.indexOf("data-") === 0 &&
        oldElement.getAttribute(attribute.name) !== attribute.value
      ) {
        oldElement.setAttribute(attribute.name, attribute.value);
      }
    });
    return false;
  }

  // Elements that left the document without a removal callback (moved aside by the morph, or replaced wholesale).
  function sweepDetached(): void {
    mounted.forEach(function (entry, element) {
      if (!element.isConnected) destroyHook(element);
    });
  }

  function registerHook(name: string, hook: JaspringHook<any, any>): void {
    hooks[name] = hook;
    mountOrUpdate(document.documentElement);
  }

  function registerCall(name: string, fn: JaspringCallFn): void {
    calls[name] = fn;
  }

  // ---------- calls from the server ----------

  function resolveCall(message: CallMessage): unknown {
    const target = String(message.target);
    const args = message.args || [];
    const dot = target.indexOf(".");
    if (dot < 0) {
      const fn = calls[target];
      if (typeof fn !== "function")
        throw new Error('No browser function registered as "' + target + '"');
      return fn.apply(null, args);
    }
    const hookName = target.substring(0, dot);
    const method = target.substring(dot + 1);
    const host =
      message.v === undefined || message.v === null
        ? document.documentElement
        : document.querySelector('[data-jsp-v="' + message.v + '"]');
    if (!host) throw new Error("View " + message.v + " is not in the document");
    const element =
      host.getAttribute("data-jsp-hook") === hookName
        ? host
        : host.querySelector('[data-jsp-hook="' + hookName + '"]');
    const entry = element && mounted.get(element);
    if (!entry)
      throw new Error(
        'No mounted hook "' + hookName + '" inside view ' + message.v,
      );
    if (typeof entry.hook[method] !== "function")
      throw new Error('Hook "' + hookName + '" has no method "' + method + '"');
    return callHook(entry, method, args);
  }

  function invoke(message: CallMessage): void {
    let outcome: Promise<unknown>;
    try {
      outcome = Promise.resolve(resolveCall(message));
    } catch (error) {
      outcome = Promise.reject(error);
    }
    outcome.then(
      function (value) {
        send({
          t: "result",
          page: pageId,
          id: message.id,
          value: value === undefined ? null : value,
        });
      },
      function (error) {
        send({
          t: "result",
          page: pageId,
          id: message.id,
          error: {
            message: error && error.message ? error.message : String(error),
          },
        });
      },
    );
  }

  // ---------- patches ----------

  function handle(message: ServerMessage): void {
    switch (message.t) {
      case "patch": {
        if (message.seq && message.seq <= lastSeq) return;
        if (message.seq) lastSeq = message.seq;
        const modelState: ModelState = {};
        const patched: string[] = [];
        message.patches.forEach(function (patch) {
          const host = document.querySelector('[data-jsp-v="' + patch.v + '"]');
          if (!host) return;
          modelStateFrom(patch.html, modelState);
          if (window.Idiomorph) {
            window.Idiomorph.morph(host, patch.html, {
              morphStyle: "outerHTML",
              ignoreActiveValue: true,
              callbacks: {
                beforeNodeRemoved: destroyIn,
                beforeNodeMorphed: keepIgnored,
              },
            });
          } else {
            host.outerHTML = patch.html;
          }
          patched.push(patch.v);
        });
        syncModels(modelState);
        scan();
        patched.forEach(function (v) {
          const host = document.querySelector('[data-jsp-v="' + v + '"]');
          if (host) mountOrUpdate(host);
        });
        sweepDetached();
        break;
      }
      case "call":
        invoke(message);
        break;
      case "redirect":
        window.location.assign(message.url);
        break;
      case "error":
        if (message.code === "unknown-page") {
          window.location.reload();
          return;
        }
        console.error("[jaspring] " + message.code + ": " + message.message);
        break;
      case "ping":
        send({ t: "pong" });
        break;
    }
  }

  function send(message: ClientMessage): void {
    transport.send(message);
  }

  // ---------- WebSocket transport ----------

  function webSocketTransport(): Transport {
    let socket: WebSocket | null = null;
    const queue: ClientMessage[] = [];
    let everOpened = false;
    let joined = false;
    let attempts = 0;
    const api: Transport = {
      send: function (message) {
        if (!(socket && socket.readyState === WebSocket.OPEN && joined)) {
          if (message.t !== "pong") queue.push(message);
          return;
        }
        const json = encodeForSocket(message);
        if (json !== null) socket.send(json);
      },
    };

    // The server closes the socket on a message above its limit, so an oversized result fails its call instead.
    function encodeForSocket(message: ClientMessage): string | null {
      const json = JSON.stringify(message);
      if (json.length <= MAX_SOCKET_MESSAGE_CHARS) return json;
      if (message.t === "result") {
        return JSON.stringify({
          t: "result",
          page: pageId,
          id: message.id,
          error: {
            message:
              "Result is " +
              json.length +
              " characters; the WebSocket limit is " +
              MAX_SOCKET_MESSAGE_CHARS,
          },
        });
      }
      console.error(
        "[jaspring] dropped a " +
          message.t +
          " message of " +
          json.length +
          " characters, above the WebSocket limit",
      );
      return null;
    }

    function connect(): void {
      const url =
        (location.protocol === "https:" ? "wss://" : "ws://") +
        location.host +
        "/_jaspring/ws";
      const opened = new WebSocket(url);
      socket = opened;
      opened.onopen = function () {
        everOpened = true;
        attempts = 0;
        opened.send(JSON.stringify({ t: "join", page: pageId }));
        joined = true;
        const pending = queue.splice(0);
        pending.forEach(api.send);
      };
      opened.onmessage = function (event) {
        handle(JSON.parse(event.data));
      };
      opened.onclose = function () {
        joined = false;
        if (!everOpened && transportName === "websocket") {
          switchToHttp(queue.splice(0));
          return;
        }
        const delay =
          Math.min(10000, 500 * Math.pow(2, attempts++)) *
          (0.8 + Math.random() * 0.4);
        setTimeout(connect, delay);
      };
      opened.onerror = function () {
        /* onclose follows */
      };
    }

    connect();
    return api;
  }

  // ---------- HTTP transport ----------

  function httpTransport(): ResumableTransport {
    const queue: ClientMessage[] = [];
    let inFlight = false;
    let polling = false;
    const api: ResumableTransport = {
      send: function (message) {
        if (message.t === "pong") return;
        queue.push(message);
        pump();
      },
      // Work queued while the socket was failing is not lost; forked results and calls made while rendering are
      // fetched by polling once, at once when the server said messages already wait.
      resume: function (pending) {
        pending.forEach(function (message) {
          if (message.t === "event" || message.t === "result")
            queue.push(message);
        });
        pump();
        if (!polling) poll(pendingAtLoad);
      },
    };

    function headers(): Record<string, string> {
      const out: Record<string, string> = {
        "Content-Type": "application/json",
      };
      const token = document.querySelector('meta[name="_csrf"]');
      const header = document.querySelector('meta[name="_csrf_header"]');
      if (token && header)
        out[header.getAttribute("content") || ""] =
          token.getAttribute("content") || "";
      return out;
    }

    function pump(): void {
      if (inFlight || queue.length === 0) return;
      inFlight = true;
      const message = queue.shift();
      fetch("/_jaspring/event", {
        method: "POST",
        headers: headers(),
        body: JSON.stringify(message),
        credentials: "same-origin",
      })
        .then(response)
        .catch(function (error) {
          console.error("[jaspring] transport error", error);
        })
        .then(function () {
          inFlight = false;
          pump();
        });
    }

    function response(res: Response): Promise<void> | undefined {
      if (res.status === 410) {
        window.location.reload();
        return;
      }
      return res.json().then(function (body: {
        messages?: ServerMessage[];
        pending?: boolean;
      }) {
        (body.messages || []).forEach(handle);
        if (body.pending && !polling) poll();
      });
    }

    function poll(immediately?: boolean): void {
      polling = true;
      setTimeout(
        function () {
          fetch("/_jaspring/poll?page=" + encodeURIComponent(pageId), {
            credentials: "same-origin",
          })
            .then(function (res): Promise<{
              messages?: ServerMessage[];
              pending?: boolean;
            }> {
              if (res.status === 410) {
                window.location.reload();
                return Promise.resolve({ messages: [], pending: false });
              }
              return res.json();
            })
            .then(function (body) {
              (body.messages || []).forEach(handle);
              polling = false;
              if (body.pending) poll();
            })
            .catch(function () {
              polling = false;
            });
        },
        immediately ? 0 : 500,
      );
    }

    return api;
  }

  function switchToHttp(pending: ClientMessage[]): void {
    transportName = "http";
    const http = httpTransport();
    transport = http;
    http.resume(pending || []);
  }

  if (transportName === "http") {
    const http = httpTransport();
    transport = http;
    http.resume([]);
  } else {
    transport = webSocketTransport();
  }
  scan();
  window.jaspring = {
    page: pageId,
    send: send,
    transport: function () {
      return transportName;
    },
    hooks: { register: registerHook },
    calls: { register: registerCall },
  };
  mountOrUpdate(document.documentElement);
})();
