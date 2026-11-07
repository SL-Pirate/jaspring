import fs from 'node:fs';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { createRequire, builtinModules, registerHooks } from 'node:module';
import { pathToFileURL, fileURLToPath } from 'node:url';
import readline from 'node:readline';

const slash = value => value.split(path.sep).join('/');
const digest = value => createHash('sha256').update(value).digest('hex');
const read = file => JSON.parse(fs.readFileSync(file, 'utf8'));
const write = (file, value) => {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  const text = typeof value === 'string' ? value : JSON.stringify(value, null, 2) + '\n';
  if (!fs.existsSync(file) || fs.readFileSync(file, 'utf8') !== text) fs.writeFileSync(file, text);
};
const within = (root, file) => file === root || file.startsWith(root + path.sep);
const packageName = name => name.startsWith('@') ? name.split('/').slice(0, 2).join('/') : name.split('/')[0];
const bare = name => !name.startsWith('.') && !path.isAbsolute(name);
const builtins = new Set(builtinModules.flatMap(name => [name.replace(/^node:/, ''), 'node:' + name.replace(/^node:/, '')]));

function walk(directory) {
  if (!fs.existsSync(directory)) return [];
  const result = [];
  for (const item of fs.readdirSync(directory, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name))) {
    if (item.name === 'node_modules') continue;
    const file = path.join(directory, item.name);
    if (item.isDirectory()) result.push(...walk(file));
    else if (item.isFile()) result.push(file);
  }
  return result;
}

function context(configFile) {
  const config = read(configFile);
  const workspace = path.dirname(path.dirname(path.resolve(configFile)));
  const project = path.resolve(workspace, config.project ?? '..');
  const require = createRequire(path.join(workspace, 'package.json'));
  return { config, workspace, project, require, ts: require('typescript'),
    source: path.resolve(project, config.source), modules: path.join(workspace, 'node_modules'),
    types: path.join(workspace, 'jaspring/typescript/types'), configFile: path.resolve(configFile) };
}

function packageDirectory(name, from) {
  let directory = from;
  while (true) {
    const candidate = path.join(directory, 'node_modules', name);
    if (fs.existsSync(path.join(candidate, 'package.json'))) return candidate;
    const parent = path.dirname(directory);
    if (parent === directory) throw Error(`Cannot locate installed package ${name} from ${from}; run jnpmInstall`);
    directory = parent;
  }
}

function closure(ctx, names) {
  const directories = new Set();
  function visit(name, from, optional = false) {
    let directory;
    try { directory = packageDirectory(name, from); }
    catch (error) { if (optional) return; throw error; }
    if (!within(ctx.modules, directory)) throw Error(`Dependency ${name} resolved outside the JNPM installation`);
    if (directories.has(directory)) return;
    directories.add(directory);
    const pkg = read(path.join(directory, 'package.json'));
    for (const dep of Object.keys(pkg.dependencies ?? {})) if (!Object.hasOwn(pkg.optionalDependencies ?? {}, dep)) visit(dep, directory);
    for (const dep of Object.keys(pkg.optionalDependencies ?? {})) visit(dep, directory, true);
    for (const dep of Object.keys(pkg.peerDependencies ?? {})) visit(dep, directory, pkg.peerDependenciesMeta?.[dep]?.optional === true);
  }
  for (const name of names) visit(name, ctx.workspace);
  return directories;
}

function options(ctx) {
  return { ...ctx.ts.convertCompilerOptionsFromJson(read(path.join(ctx.types, 'tsconfig.base.json')).compilerOptions, ctx.types).options,
    paths: undefined, noEmit: true, resolveJsonModule: true, customConditions: ['browser'],
    typeRoots: [path.join(ctx.modules, '@types')], types: [] };
}

function publicSpecifiers(ctx, name, directory) {
  const pkg = read(path.join(directory, 'package.json'));
  const names = new Set([name]);
  const relativeFiles = walk(directory).map(file => slash(path.relative(directory, file)));
  function targets(value) {
    if (typeof value === 'string') return [value];
    if (!value || typeof value !== 'object') return [];
    return Object.values(value).flatMap(targets);
  }
  function expand(key, values) {
    for (const target of values) {
      if (!target.includes('*')) continue;
      const [prefix, suffix] = target.replace(/^\.\//, '').split('*');
      for (const file of relativeFiles) {
        if (file.startsWith(prefix) && file.endsWith(suffix) && file.length >= prefix.length + suffix.length) {
          const match = file.slice(prefix.length, suffix ? -suffix.length : undefined);
          names.add(name + '/' + key.replaceAll('*', match));
        }
      }
    }
  }
  if (pkg.exports != null) {
    const exports = typeof pkg.exports === 'object' && Object.keys(pkg.exports).some(key => key.startsWith('.')) ? pkg.exports : { '.': pkg.exports };
    for (const [key, value] of Object.entries(exports)) {
      if (value === null) continue;
      if (!key.includes('*')) { names.add(name + key.slice(1)); continue; }
      expand(key.slice(2), targets(value).filter(target => target.startsWith('./')));
    }
  } else {
    for (const file of relativeFiles) {
      if (/\.(?:[cm]?js|[cm]?ts)$/.test(file)) {
        names.add(name + '/' + file.replace(/(?:\.d)?\.[cm]?[jt]s$/, ''));
        names.add(name + '/' + file.replace(/\.d\.[cm]?ts$/, '.js'));
      }
    }
    for (const versionPaths of Object.values(pkg.typesVersions ?? {})) {
      for (const [key, values] of Object.entries(versionPaths)) {
        if (key.includes('*')) expand(key, targets(values));
        else names.add(name + '/' + key);
      }
    }
  }
  return [...names].sort();
}

function prepareEnvironment(ctx, development) {
  const { config, ts } = ctx;
  const roots = Object.keys({ ...config.scopes.implementation, ...config.scopes.api,
    ...(development ? { ...config.scopes.devImplementation, ...config.scopes.devApi } : {}) });
  const dirs = closure(ctx, roots);
  const compilerOptions = options(ctx);
  const paths = {};
  const globals = [];
  const files = {};
  const api = Object.keys({ ...config.scopes.api, ...(development ? config.scopes.devApi : {}) });
  for (const directory of [...dirs].sort()) {
    for (const file of walk(directory)) {
      if (path.basename(file) === 'package.json' || /\.(?:[cm]?ts|json)$/.test(file)) {
        files[slash(path.relative(ctx.workspace, file))] = fs.readFileSync(file, 'utf8');
      }
    }
  }
  const names = new Set(roots);
  for (const name of roots) if (name.startsWith('@types/')) {
    const typed = name.slice(7);
    names.add(typed.includes('__') ? '@' + typed.replace('__', '/') : typed);
  }
  for (const name of [...names].sort()) {
    let directory;
    try { directory = packageDirectory(name, ctx.workspace); }
    catch { directory = packageDirectory('@types/' + name.replace(/^@/, '').replace('/', '__'), ctx.workspace); }
    for (const specifier of publicSpecifiers(ctx, name, directory)) {
      const resolved = ts.resolveModuleName(specifier, path.join(ctx.workspace, '__jnpm__.ts'), compilerOptions, ts.sys).resolvedModule;
      if (resolved && within(ctx.modules, resolved.resolvedFileName)) {
        paths[specifier] = [slash(path.relative(ctx.types, resolved.resolvedFileName))];
        if (api.includes(name) && specifier === name) globals.push(slash(path.relative(ctx.workspace, resolved.resolvedFileName)));
      }
    }
  }
  const assetTypes = `declare module '*.css';\n${['svg','png','jpg','jpeg','gif','webp','ico','woff','woff2','ttf','wasm'].map(ext => `declare module '*.${ext}' { const url: string; export default url; }`).join('\n')}\n`;
  files['jnpm/assets.d.ts'] = assetTypes;
  globals.push('jnpm/assets.d.ts');
  for (const file of walk(ctx.source)) if (file.endsWith('.json')) files['src/' + slash(path.relative(ctx.source, file))] = fs.readFileSync(file, 'utf8');
  const declarations = [...new Set(globals)].sort();
  const snapshot = { files, globalTypes: declarations, allowedImports: [...names].sort() };
  const suffix = development ? '.dev' : '';
  write(path.join(ctx.workspace, `jnpm/dependencies${suffix}.json`), snapshot);
  write(path.join(ctx.types, `tsconfig.jnpm${suffix}.json`), {
    extends: './tsconfig.base.json', compilerOptions: { paths, typeRoots: [slash(path.relative(ctx.types, path.join(ctx.modules, '@types')))],
      types: [], customConditions: ['browser'], resolveJsonModule: true },
    files: ['./jaspring.d.ts', './assets.d.ts', ...declarations.filter(file => file !== 'jnpm/assets.d.ts').map(file => slash(path.relative(ctx.types, path.join(ctx.workspace, file))))],
  });
  write(path.join(ctx.types, 'assets.d.ts'), assetTypes);
  return { paths, roots, directories: [...dirs] };
}

function sourceImports(ctx) {
  const result = [];
  const { ts } = ctx;
  for (const file of walk(ctx.source).filter(file => /\.ts$/.test(file))) {
    const source = ts.createSourceFile(file, fs.readFileSync(file, 'utf8'), ts.ScriptTarget.Latest, true);
    function visit(node) {
      let specifier, typeOnly = false;
      if (ts.isImportDeclaration(node) || ts.isExportDeclaration(node)) {
        specifier = node.moduleSpecifier;
        typeOnly = node.isTypeOnly || node.importClause?.isTypeOnly ||
          (node.importClause?.namedBindings && ts.isNamedImports(node.importClause.namedBindings) && !node.importClause.name &&
            node.importClause.namedBindings.elements.length > 0 && node.importClause.namedBindings.elements.every(item => item.isTypeOnly)) ||
          (node.exportClause && ts.isNamedExports(node.exportClause) && node.exportClause.elements.length > 0 &&
            node.exportClause.elements.every(item => item.isTypeOnly));
      } else if (ts.isImportTypeNode(node)) { specifier = node.argument.literal; typeOnly = true; }
      else if (ts.isCallExpression(node) && (node.expression.kind === ts.SyntaxKind.ImportKeyword || node.expression.getText(source) === 'require')) {
        specifier = node.arguments[0];
        if (!specifier || !ts.isStringLiteralLike(specifier)) throw Error(`${file}: dynamic imports/requires must use a literal module name`);
      }
      if (specifier && ts.isStringLiteralLike(specifier)) result.push({ file, name: specifier.text, typeOnly, start: specifier.getStart(source) });
      ts.forEachChild(node, visit);
    }
    visit(source);
  }
  return result;
}

function validateImports(ctx) {
  const runtime = new Set(Object.keys(ctx.config.scopes.implementation));
  const types = new Set([...runtime, ...Object.keys(ctx.config.scopes.api).flatMap(name => {
    if (!name.startsWith('@types/')) return [name];
    const typed = name.slice(7);
    return [name, typed.includes('__') ? '@' + typed.replace('__', '/') : typed];
  })]);
  types.add('jaspring');
  for (const item of sourceImports(ctx)) {
    if (!bare(item.name)) continue;
    if (builtins.has(item.name) || item.name.startsWith('node:')) throw Error(`${item.file}: '${item.name}' is a Node/OS API and cannot run in a browser`);
    if (!(item.typeOnly ? types : runtime).has(packageName(item.name))) {
      throw Error(`${item.file}: '${item.name}' needs a JNPM ${item.typeOnly ? 'api or implementation' : 'implementation'} declaration; development and transitive-only packages are not application imports`);
    }
  }
}

async function bundle(ctx, output, manifest) {
  validateImports(ctx);
  const esbuild = ctx.require('esbuild');
  const stage = output + '.staging';
  fs.rmSync(stage, { recursive: true, force: true });
  fs.mkdirSync(stage, { recursive: true });
  const sources = walk(ctx.source).filter(file => file.endsWith('.ts'));
  const entries = sources.filter(file => path.dirname(file) === ctx.source && !file.endsWith('.d.ts'));
  try {
    const result = entries.length ? await esbuild.build({
      absWorkingDir: ctx.project, entryPoints: entries, outdir: stage, outbase: ctx.source,
      bundle: true, format: 'esm', platform: 'browser', target: 'es2022', splitting: true,
      minify: true, sourcemap: true, sourcesContent: true, metafile: true, treeShaking: true,
      entryNames: '[name]-[hash]', chunkNames: 'chunks/[name]-[hash]', assetNames: 'assets/[name]-[hash]',
      legalComments: 'linked', nodePaths: [ctx.modules], logLevel: 'info',
      tsconfigRaw: { compilerOptions: { target: 'ES2022', useDefineForClassFields: true, verbatimModuleSyntax: true } },
      define: { 'process.env.NODE_ENV': '"production"' },
      loader: Object.fromEntries(['svg','png','jpg','jpeg','gif','webp','ico','woff','woff2','ttf','wasm'].map(ext => ['.' + ext, 'file'])),
      plugins: [{ name: 'jnpm-browser-only', setup(build) {
        build.onResolve({ filter: /.*/ }, args => {
          if (builtins.has(args.path) || args.path.startsWith('node:')) return { errors: [{ text: `'${args.path}' is a Node/OS API; JNPM only bundles browser code (imported by ${args.importer})` }] };
          if (args.kind !== 'url-token' && /^(?:https?:|file:|data:)/.test(args.path)) return { errors: [{ text: `External runtime imports are not supported: ${args.path}` }] };
          return undefined;
        });
      }}],
    }) : { metafile: { inputs: {}, outputs: {} } };
    const runtimeDirs = closure(ctx, Object.keys(ctx.config.scopes.implementation));
    for (const input of Object.keys(result.metafile.inputs)) {
      const absolute = path.resolve(ctx.project, input);
      if (within(ctx.modules, absolute)) {
        const relative = slash(path.relative(ctx.modules, absolute));
        const split = relative.lastIndexOf('/node_modules/');
        const prefix = split < 0 ? '' : relative.slice(0, split + '/node_modules/'.length);
        const directory = path.join(ctx.modules, prefix, packageName(relative.slice(prefix.length)));
        if (!runtimeDirs.has(directory)) throw Error(`Development or declaration-only package entered the browser bundle: ${input}`);
      }
    }
    const entryOutputs = new Map();
    for (const [file, meta] of Object.entries(result.metafile.outputs)) {
      if (meta.entryPoint && entries.includes(path.resolve(ctx.project, meta.entryPoint)) && file.endsWith('.js')) {
        entryOutputs.set(path.resolve(ctx.project, meta.entryPoint), slash(path.relative(stage, path.resolve(ctx.project, file))));
      }
    }
    const notices = [];
    for (const directory of [...runtimeDirs].sort()) {
      if (!Object.keys(result.metafile.inputs).some(input => within(directory, path.resolve(ctx.project, input)))) continue;
      const pkg = read(path.join(directory, 'package.json'));
      const licenses = fs.readdirSync(directory).filter(name => /^(?:licen[cs]e|copying|notice)(?:[.-].*)?$/i.test(name));
      for (const name of licenses) if (fs.statSync(path.join(directory, name)).isFile()) notices.push(`${pkg.name} ${pkg.version}\n${fs.readFileSync(path.join(directory, name), 'utf8')}`);
    }
    write(path.join(stage, 'THIRD-PARTY-LICENSES.txt'), notices.join('\n\n'));
    const assets = walk(stage).map(file => ({ output: slash(path.relative(stage, file)), sha256: digest(fs.readFileSync(file)) }));
    const files = sources.map(file => ({ source: slash(path.relative(ctx.source, file)), sha256: digest(fs.readFileSync(file)), output: entryOutputs.get(file) ?? null }));
    const lock = path.join(ctx.project, 'jnpm.lock');
    const data = { version: 2, bundled: true, typescript: ctx.ts.version, esbuild: esbuild.version,
      lockSha256: digest(fs.readFileSync(lock)), files, assets,
      inputs: Object.fromEntries(walk(ctx.source).map(file => [slash(path.relative(ctx.source, file)), digest(fs.readFileSync(file))])),
      entries: files.filter(file => file.output).map(file => file.output), styles: assets.filter(file => file.output.endsWith('.css')).map(file => file.output) };
    const backup = output + '.previous';
    const nextManifest = manifest + '.next';
    write(nextManifest, data);
    fs.rmSync(backup, { recursive: true, force: true });
    const hadOutput = fs.existsSync(output);
    if (hadOutput) fs.renameSync(output, backup);
    try {
      fs.renameSync(stage, output);
      fs.renameSync(nextManifest, manifest);
    } catch (error) {
      fs.rmSync(output, { recursive: true, force: true });
      if (hadOutput) fs.renameSync(backup, output);
      throw error;
    } finally { fs.rmSync(nextManifest, { force: true }); }
    fs.rmSync(backup, { recursive: true, force: true });
  } finally { fs.rmSync(stage, { recursive: true, force: true }); }
}

function installConfigResolution(ctx) {
  const anchor = pathToFileURL(path.join(ctx.workspace, '__jnpm_config__.mjs')).href;
  registerHooks({ resolve(specifier, context, nextResolve) {
    // Only application-owned configuration imports use this fallback. Package-internal failures must stay failures.
    const parent = context.parentURL?.startsWith('file:') ? fileURLToPath(context.parentURL) : null;
    if (parent && within(ctx.project, parent) && !within(ctx.modules, parent) && bare(specifier) && !specifier.includes(':')) {
      return nextResolve(specifier, { ...context, parentURL: anchor });
    }
    return nextResolve(specifier, context);
  }});
}

async function linter(ctx, fix) {
  installConfigResolution(ctx);
  const { ESLint } = ctx.require('eslint');
  const file = path.join(ctx.project, 'eslint.config.mjs');
  const defaults = ctx.require('@eslint/js').configs.recommended;
  const typescript = ctx.require('typescript-eslint');
  const globals = ctx.require('globals');
  return new ESLint({ cwd: ctx.project, fix, overrideConfigFile: fs.existsSync(file) ? file : true,
    ...(!fs.existsSync(file) ? { overrideConfig: [
      { ignores: ['**/build/**', '**/node_modules/**'] }, defaults,
      ...typescript.configs.recommended,
      { files: ['**/*.ts'], languageOptions: { globals: { ...globals.browser, jaspring: 'readonly' } } },
    ] } : {}) });
}

async function lint(ctx, fix) {
  const eslint = await linter(ctx, fix);
  const files = walk(ctx.source).filter(file => /\.[jt]s$/.test(file) && !file.endsWith('.d.ts'));
  const results = files.length ? await eslint.lintFiles(files) : [];
  if (fix) await ctx.require('eslint').ESLint.outputFixes(results);
  write(path.join(ctx.workspace, 'reports/jnpm/eslint.json'), results);
  for (const result of results) for (const message of result.messages) {
    console.log(`${slash(path.relative(ctx.project, result.filePath))}(${message.line ?? 1},${message.column ?? 1}): ${message.severity === 2 ? 'error' : 'warning'} ${message.ruleId ?? 'eslint'}: ${message.message}`);
  }
  if (results.some(result => result.errorCount > 0)) process.exitCode = 1;
}

async function lintServer(ctx) {
  const eslint = await linter(ctx, false);
  for await (const line of readline.createInterface({ input: process.stdin })) {
    try {
      const request = JSON.parse(line);
      const file = path.resolve(ctx.project, request.file);
      if (!within(ctx.project, file) || within(ctx.modules, file)) throw Error('File is outside the application sources');
      const results = await eslint.lintText(request.text, { filePath: file });
      console.log(JSON.stringify({ id: request.id, messages: results[0]?.messages ?? [] }));
    } catch (error) { console.log(JSON.stringify({ error: error.message })); }
  }
}

async function main() {
  const [command, configFile, ...args] = process.argv.slice(2);
  const ctx = context(configFile);
  if (command === 'prepare') {
    prepareEnvironment(ctx, false);
    prepareEnvironment(ctx, true);
    const toolchain = read(path.join(ctx.workspace, 'jnpm/toolchain.json'));
    write(path.join(ctx.workspace, 'jnpm/ide.json'), { version: 1, project: ctx.project, source: ctx.source,
      modules: ctx.modules, config: ctx.configFile, bridge: fileURLToPath(import.meta.url), node: toolchain.node,
      lint: ctx.config.lint, scopes: ctx.config.scopes, tsconfig: path.join(ctx.types, 'tsconfig.jnpm.json') });
  } else if (command === 'validate') validateImports(ctx);
  else if (command === 'bundle') await bundle(ctx, path.resolve(args[0]), path.resolve(args[1]));
  else if (command === 'lint' || command === 'fix') await lint(ctx, command === 'fix');
  else if (command === 'lint-server') await lintServer(ctx);
  else throw Error(`Unknown JNPM operation '${command}'`);
}

main().catch(error => { console.error(error.message); process.exitCode = 1; });
