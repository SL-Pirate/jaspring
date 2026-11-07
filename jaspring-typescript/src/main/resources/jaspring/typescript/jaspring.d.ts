// Typings of the `jaspring` browser global for application scripts. Generated from tools/shim/src/public.d.ts;
// do not edit.
interface JaspringHookContext<P = unknown> {
  readonly page: string;
  readonly el: HTMLElement;
  readonly name: string;
  /** Re-read from `data-jsp-hook-params` before every `updated`. */
  params: P;
  /** Dispatches a bubbling CustomEvent the template receives with `(type)="handle($event.detail)"`. */
  pushEvent(type: string, detail?: unknown): void;
}

interface JaspringHook<E extends Element = HTMLElement, P = unknown> {
  mounted?(el: E, ctx: JaspringHookContext<P>): void;
  updated?(el: E, ctx: JaspringHookContext<P>): void;
  destroyed?(el: E, ctx: JaspringHookContext<P>): void;
  /** Any other method answers `Browser.call("hook.method")` with `(el, ctx, ...args)`; it may return a promise. */
  [method: string]:
    | ((el: E, ctx: JaspringHookContext<P>, ...args: any[]) => unknown)
    | undefined;
}

type JaspringCallFn = (...args: any[]) => unknown;

interface JaspringGlobal {
  readonly page: string;
  send(message: unknown): void;
  transport(): "websocket" | "http";
  hooks: {
    register<E extends Element = HTMLElement, P = unknown>(
      name: string,
      hook: JaspringHook<E, P>,
    ): void;
  };
  calls: { register(name: string, fn: JaspringCallFn): void };
  /** Present when the page carries a WebAssembly build of `Platform.onBrowser` closures. */
  wasm?: { ensure(): Promise<void>; warm(): void; loaded(): boolean };
}

declare var jaspring: JaspringGlobal;

interface Window {
  jaspring: JaspringGlobal;
}

declare module "jaspring" {
  export type HookContext<P = unknown> = JaspringHookContext<P>;
  export type Hook<E extends Element = HTMLElement, P = unknown> = JaspringHook<
    E,
    P
  >;
  export type CallFn = JaspringCallFn;
  export type Global = JaspringGlobal;
}
