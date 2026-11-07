import ts from "typescript";
import { libs } from "jaspring:typescript-libs";
import { options } from "./typescript-options.mjs";

export { options };

export const versions = __JASPRING_VERSIONS__;

const SRC = "/src/";
const TYPES = "/types/";
const LIB = "/lib/";
const OUT = "/out/";

const libSourceFiles = new Map();

/// One string in, one string out: the boundary both the GraalJS host and the Node driver use. Nothing is
/// thrown, so a compiler crash is reported the same way on both runtimes.
export function run(requestJson) {
  try {
    const request = JSON.parse(requestJson);
    switch (request.op) {
      case "version":
        return JSON.stringify({
          typescript: ts.version,
          sys: ts.sys !== undefined,
        });
      case "options":
        return JSON.stringify(options);
      case "transpile":
        return JSON.stringify(transpile(request.file, request.options ?? {}));
      case "compile":
        return JSON.stringify(
          compile(
            request.files ?? [],
            request.types ?? [],
            request.options ?? {},
            request.dependencies ?? {},
          ),
        );
      default:
        throw new Error(`Unknown op '${request.op}'`);
    }
  } catch (error) {
    return JSON.stringify({
      error: {
        message: String(error?.message ?? error),
        stack: error?.stack ?? null,
      },
    });
  }
}

function compilerOptions(requestOptions) {
  const converted = ts.convertCompilerOptionsFromJson(
    {
      ...options,
      sourceMap: requestOptions.sourceMap === true,
      noEmit: requestOptions.noEmit === true,
      ...(requestOptions.packages ? { resolveJsonModule: true, customConditions: ["browser"] } : {}),
      rootDir: SRC,
      outDir: OUT,
    },
    "/",
  );
  if (converted.errors.length) {
    throw new Error(
      converted.errors
        .map((d) => ts.flattenDiagnosticMessageText(d.messageText, "\n"))
        .join("\n"),
    );
  }
  return converted.options;
}

/// Type-checks and emits every file against the bundled libs and the ambient `types`. With `typeCheck` off each
/// file is transpiled on its own, which is what makes a stale-build fallback at startup cheap.
export function compile(files, types, requestOptions, dependencies = {}) {
  if (requestOptions.typeCheck === false) {
    const outputs = [];
    const diagnostics = [];
    for (const file of files) {
      if (file.path.endsWith(".d.ts")) continue;
      const result = transpile(file, requestOptions);
      outputs.push(...result.outputs);
      diagnostics.push(...result.diagnostics);
    }
    return { outputs: requestOptions.noEmit ? [] : outputs, diagnostics };
  }
  const compilerOpts = compilerOptions({ ...requestOptions, packages: Object.keys(dependencies.files ?? {}).length > 0 });
  const sources = new Map();
  for (const file of files) sources.set(SRC + file.path, file.text);
  for (const file of types) sources.set(TYPES + file.path, file.text);
  const roots = [...sources.keys(), ...(dependencies.globalTypes ?? []).map(path => "/" + path)];
  for (const [path, text] of Object.entries(dependencies.files ?? {})) sources.set("/" + path, text);
  const outputs = [];
  const host = memoryHost(sources, compilerOpts, outputs);
  if (Object.keys(dependencies.files ?? {}).length > 0) {
    const allowed = new Set([...(dependencies.allowedImports ?? []), 'jaspring']);
    host.resolveModuleNameLiterals = (literals, containingFile) => literals.map(literal => {
      const name = literal.text;
      const root = name.startsWith('@') ? name.split('/').slice(0, 2).join('/') : name.split('/')[0];
      if (containingFile.startsWith(SRC) && !name.startsWith('.') && !allowed.has(root)) return { resolvedModule: undefined };
      return ts.resolveModuleName(name, containingFile, compilerOpts, host);
    });
  }
  const program = ts.createProgram(roots, compilerOpts, host);
  const diagnostics = [...ts.getPreEmitDiagnostics(program)];
  diagnostics.push(...program.emit().diagnostics);
  return { outputs, diagnostics: diagnostics.map(diagnostic) };
}

export function transpile(file, requestOptions) {
  const compilerOpts = compilerOptions(requestOptions);
  const result = ts.transpileModule(file.text, {
    compilerOptions: compilerOpts,
    fileName: SRC + file.path,
    reportDiagnostics: true,
  });
  const outputPath = file.path.replace(/\.ts$/, ".js");
  const outputs = [{ path: outputPath, text: result.outputText }];
  if (result.sourceMapText !== undefined)
    outputs.push({ path: outputPath + ".map", text: result.sourceMapText });
  return { outputs, diagnostics: (result.diagnostics ?? []).map(diagnostic) };
}

function memoryHost(sources, compilerOpts, outputs) {
  const directories = new Set(["/", "/src", "/types", "/lib", "/out"]);
  for (const path of sources.keys()) {
    for (let dir = dirname(path); dir !== "/"; dir = dirname(dir))
      directories.add(dir);
  }
  const libPath = (fileName) =>
    fileName.startsWith(LIB) ? fileName.slice(LIB.length) : null;
  const read = (fileName) => {
    const lib = libPath(fileName);
    return lib !== null ? libs[lib] : sources.get(fileName);
  };
  return {
    getSourceFile(fileName, languageVersion) {
      const lib = libPath(fileName);
      if (lib !== null) {
        if (!(lib in libs)) return undefined;
        // Lib declarations never change: parse each once per process so warm compiles skip the 2.5 MB of DOM types.
        let cached = libSourceFiles.get(lib);
        if (!cached) {
          cached = ts.createSourceFile(fileName, libs[lib], languageVersion);
          libSourceFiles.set(lib, cached);
        }
        return cached;
      }
      const text = sources.get(fileName);
      return text === undefined
        ? undefined
        : ts.createSourceFile(fileName, text, languageVersion);
    },
    getDefaultLibLocation: () => "/lib",
    getDefaultLibFileName: (opts) => LIB + ts.getDefaultLibFileName(opts),
    writeFile(fileName, text) {
      outputs.push({
        path: fileName.startsWith(OUT) ? fileName.slice(OUT.length) : fileName,
        text,
      });
    },
    getCurrentDirectory: () => "/",
    getCanonicalFileName: (fileName) => fileName,
    useCaseSensitiveFileNames: () => true,
    getNewLine: () => "\n",
    fileExists: (fileName) => read(fileName) !== undefined,
    readFile: read,
    directoryExists: (dir) => directories.has(dir.replace(/\/$/, "") || "/"),
    getDirectories: () => [],
    realpath: (path) => path,
  };
}

function dirname(path) {
  const slash = path.lastIndexOf("/");
  return slash <= 0 ? "/" : path.slice(0, slash);
}

function diagnostic(d) {
  let file = null;
  let line = 0;
  let column = 0;
  if (d.file) {
    file = relative(d.file.fileName);
    if (d.start !== undefined) {
      const position = d.file.getLineAndCharacterOfPosition(d.start);
      line = position.line + 1;
      column = position.character + 1;
    }
  }
  return {
    file,
    line,
    column,
    code: d.code,
    category: ts.DiagnosticCategory[d.category].toLowerCase(),
    message: ts.flattenDiagnosticMessageText(d.messageText, "\n"),
  };
}

function relative(fileName) {
  for (const root of [SRC, TYPES]) {
    if (fileName.startsWith(root)) return fileName.slice(root.length);
  }
  return fileName.startsWith("/") ? fileName.slice(1) : fileName;
}
