// The committed JavaScript and typings must be what the TypeScript sources produce: edit src/, run `pnpm build`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import path from "node:path";
import { emit, root } from "../lib/emit.mjs";

test("committed shim and typings match the TypeScript sources", () => {
  const { outputs, diagnostics } = emit();
  assert.deepEqual(diagnostics, []);
  assert.ok(
    outputs.size >= 3,
    "jaspring.js, jaspring-wasm.js and jaspring.d.ts",
  );
  for (const [file, text] of outputs) {
    assert.equal(
      readFileSync(file, "utf8"),
      text,
      `${path.relative(root, file)} is out of date: run pnpm build in tools/shim`,
    );
  }
});
