// Compiles src/ with tsconfig.json and returns what the build writes, without writing it, so the drift test in
// test/build.test.mjs can compare the committed files with the sources.
import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import ts from "typescript";

export const root = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "..",
);
export const typesTarget = path.resolve(
  root,
  "../../jaspring-typescript/src/main/resources/jaspring/typescript/jaspring.d.ts",
);

const TYPES_BANNER =
  "// Typings of the `jaspring` browser global for application scripts. Generated from tools/shim/src/public.d.ts;\n// do not edit.\n";

export function emit() {
  const config = ts.getParsedCommandLineOfConfigFile(
    path.join(root, "tsconfig.json"),
    {},
    {
      ...ts.sys,
      onUnRecoverableConfigFileDiagnostic: (diagnostic) => {
        throw new Error(
          ts.flattenDiagnosticMessageText(diagnostic.messageText, "\n"),
        );
      },
    },
  );
  const program = ts.createProgram(config.fileNames, config.options);
  const outputs = new Map();
  const result = program.emit(undefined, (fileName, text) =>
    outputs.set(path.resolve(fileName), text),
  );
  const diagnostics = [
    ...config.errors,
    ...ts.getPreEmitDiagnostics(program),
    ...result.diagnostics,
  ].map(format);
  outputs.set(
    typesTarget,
    TYPES_BANNER + readFileSync(path.join(root, "src/public.d.ts"), "utf8"),
  );
  return { outputs, diagnostics };
}

function format(diagnostic) {
  const message = ts.flattenDiagnosticMessageText(diagnostic.messageText, "\n");
  if (!diagnostic.file) return `error TS${diagnostic.code}: ${message}`;
  const { line, character } = diagnostic.file.getLineAndCharacterOfPosition(
    diagnostic.start ?? 0,
  );
  return `${path.relative(root, diagnostic.file.fileName)}(${line + 1},${character + 1}): error TS${diagnostic.code}: ${message}`;
}
