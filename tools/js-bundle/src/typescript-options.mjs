/// The fixed compiler option table. jaspring-typescript ships the same values as tsconfig.base.json for editors
/// and a test keeps the two equal. `verbatimModuleSyntax` plus `isolatedModules` make transpileModule and
/// program emit produce the same JavaScript, so transpile-only and type-checked builds never differ in output.
/// `moduleDetection: force` makes every file an ES module, which is how the server serves them.
export const options = {
  target: "es2022",
  module: "esnext",
  moduleResolution: "bundler",
  moduleDetection: "force",
  lib: ["es2022", "dom"],
  strict: true,
  isolatedModules: true,
  verbatimModuleSyntax: true,
  rewriteRelativeImportExtensions: true,
  allowImportingTsExtensions: true,
  skipLibCheck: true,
  types: [],
  useDefineForClassFields: true,
  newLine: "lf",
};
