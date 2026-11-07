// Globals the shim reads but does not own: the vendored Idiomorph, and what the Web Image launcher and the
// WebAssembly module install. Private to the shim; application typings are in public.d.ts.
interface JaspringWasmLauncher {
  run(args: string[], config: object): Promise<unknown> | unknown;
  Config?: new () => { wasm_path?: string };
}

interface JaspringWasmEnvelope {
  value?: unknown;
  error?: string;
}

interface JaspringWasmModule {
  run(
    id: string,
    host: unknown,
    args: unknown[],
  ): Promise<JaspringWasmEnvelope | null> | JaspringWasmEnvelope | null;
}

interface Window {
  Idiomorph?: { morph(node: Element, html: string, options: object): void };
  JaspringWasm?: JaspringWasmLauncher;
  jaspringWasm?: JaspringWasmModule;
}
