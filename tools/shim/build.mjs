// Writes jaspring.js and jaspring-wasm.js into the Spring Boot starter and jaspring.d.ts into jaspring-typescript.
// Commit the results; `pnpm test` fails when they no longer match the sources.
import { writeFileSync } from "node:fs";
import path from "node:path";
import { emit, root } from "./lib/emit.mjs";

const { outputs, diagnostics } = emit();
if (diagnostics.length) {
  console.error(diagnostics.join("\n"));
  process.exit(1);
}
for (const [file, text] of outputs) {
  writeFileSync(file, text);
  console.log(`${path.relative(root, file)}: ${text.length} chars`);
}
