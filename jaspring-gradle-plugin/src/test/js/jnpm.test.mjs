import { test, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

const workspace = process.env.JNPM_TEST_WORKSPACE;
assert.ok(workspace, 'JNPM_TEST_WORKSPACE must name the installed sample tooling');
const bridge = path.resolve(import.meta.dirname, '../../main/resources/jaspring/jnpm/bridge.mjs');
const root = path.resolve(import.meta.dirname, '../../../..');
const fixtures = [];
afterEach(() => { for (const directory of fixtures.splice(0)) fs.rmSync(directory, { recursive: true, force: true }); });

function fixture(scopes = {}) {
  const project = fs.mkdtempSync(path.join(os.tmpdir(), 'jnpm-bridge-'));
  fixtures.push(project);
  const build = path.join(project, 'build');
  const modules = path.join(build, 'node_modules');
  fs.mkdirSync(modules, { recursive: true });
  for (const name of ['esbuild', '@esbuild', 'typescript', 'eslint', '@eslint', 'typescript-eslint', 'globals']) {
    fs.symlinkSync(path.join(workspace, 'node_modules', name), path.join(modules, name), 'junction');
  }
  const put = (file, value) => {
    const target = path.join(project, file);
    fs.mkdirSync(path.dirname(target), { recursive: true });
    fs.writeFileSync(target, typeof value === 'string' ? value : JSON.stringify(value));
  };
  const config = { version: 1, source: 'src/main/typescript', project: '..', lint: true,
    scopes: { implementation: {}, api: {}, devImplementation: {}, devApi: {}, ...scopes } };
  put('build/jnpm/config.json', config);
  put('build/jnpm/toolchain.json', { node: process.execPath });
  put('build/package.json', { type: 'module' });
  put('jnpm.lock', { fixture: 1 });
  for (const name of ['tsconfig.base.json', 'jaspring.d.ts']) {
    put('build/jaspring/typescript/types/' + name, fs.readFileSync(path.join(root, 'jaspring-typescript/src/main/resources/jaspring/typescript', name), 'utf8'));
  }
  put('src/main/typescript/page.ts', 'console.log("fixture");');
  const run = (command, ...args) => spawnSync(process.execPath, [bridge, command, path.join(build, 'jnpm/config.json'), ...args],
    { encoding: 'utf8', cwd: project, timeout: 30000 });
  const bundle = () => run('bundle', path.join(build, 'js'), path.join(build, 'manifest.json'));
  const pkg = (name, manifest, files) => {
    put(`build/node_modules/${name}/package.json`, { name, version: '1.0.0', ...manifest });
    for (const [name2, contents] of Object.entries(files)) put(`build/node_modules/${name}/${name2}`, contents);
  };
  return { project, build, modules, put, config, run, bundle, pkg };
}

function succeeds(result) { assert.equal(result.status, 0, result.stdout + result.stderr); }

test('browser exports exclude the Node implementation and share singleton modules across entries', () => {
  const f = fixture({ implementation: { example: '1.0.0' } });
  f.pkg('example', { type: 'module', exports: { browser: './browser.js', default: './node.js' } }, {
    'browser.js': 'globalThis.initializations = (globalThis.initializations || 0) + 1; export const value = globalThis.initializations;',
    'node.js': 'import fs from "node:fs"; export const value = fs.readFileSync("secret");',
  });
  f.put('src/main/typescript/page.ts', 'import {value} from "example"; globalThis.first = value;');
  f.put('src/main/typescript/second.ts', 'import {value} from "example"; globalThis.second = value;');
  succeeds(f.bundle());
  const manifest = JSON.parse(fs.readFileSync(path.join(f.build, 'manifest.json')));
  assert.equal(manifest.entries.length, 2);
  assert.ok(manifest.assets.some(file => file.output.startsWith('chunks/')));
  const script = manifest.entries.map(file => `await import(${JSON.stringify(path.join(f.build, 'js', file))});`).join('\n') +
    '\nif(globalThis.first!==1||globalThis.second!==1||globalThis.initializations!==1)throw Error("Duplicated singleton");';
  succeeds(spawnSync(process.execPath, ['--input-type=module', '-e', script], { encoding: 'utf8' }));
});

test('direct and transitive Node APIs fail and leave the previous bundle untouched', () => {
  const f = fixture({ implementation: { example: '1.0.0' } });
  f.pkg('example', { main: 'index.js' }, { 'index.js': 'module.exports = 1;' });
  f.put('src/main/typescript/page.ts', 'import value from "example"; console.log(value);');
  succeeds(f.bundle());
  const original = fs.readFileSync(path.join(f.build, 'manifest.json'), 'utf8');
  for (const builtin of ['fs', 'node:fs', 'path', 'node:child_process', 'net']) {
    f.put('build/node_modules/example/index.js', `const api = require('${builtin}'); module.exports = api;`);
    const result = f.bundle();
    assert.notEqual(result.status, 0, builtin);
    assert.match(result.stderr, /Node\/OS API/);
    assert.equal(fs.readFileSync(path.join(f.build, 'manifest.json'), 'utf8'), original);
  }
  f.put('src/main/typescript/page.ts', 'import fs from "node:fs"; console.log(fs);');
  assert.match(f.bundle().stderr, /Node\/OS API/);
});

test('development and declaration scopes cannot become browser runtime imports', () => {
  const f = fixture({ api: { declarations: '1.0.0' }, devImplementation: { tool: '1.0.0' } });
  for (const name of ['declarations', 'tool', 'undeclared']) {
    f.put('src/main/typescript/page.ts', `import value from '${name}'; console.log(value);`);
    assert.match(f.bundle().stderr, /implementation declaration/);
  }
  f.put('src/main/typescript/page.ts', 'export {type Declaration} from "declarations";');
  succeeds(f.run('validate'));
});

test('declarations respect public exports, wildcard subpaths, and private export exclusions', () => {
  const f = fixture({ implementation: { example: '1.0.0' } });
  f.pkg('example', { exports: { '.': { types: './types/index.d.ts', default: './index.js' },
    './features/*': { types: './types/*.d.ts', default: './features/*.js' }, './features/private': null } }, {
    'types/index.d.ts': 'export declare const value: number;', 'types/extra.d.ts': 'export declare const extra: string;',
    'types/private.d.ts': 'export declare const secret: string;', 'index.js': 'export const value = 1;',
  });
  succeeds(f.run('prepare'));
  const config = JSON.parse(fs.readFileSync(path.join(f.build, 'jaspring/typescript/types/tsconfig.jnpm.json')));
  assert.ok(config.compilerOptions.paths.example);
  assert.ok(config.compilerOptions.paths['example/features/extra']);
  assert.equal(config.compilerOptions.paths['example/features/private'], undefined);
  const snapshot = JSON.parse(fs.readFileSync(path.join(f.build, 'jnpm/dependencies.json')));
  assert.ok(snapshot.files['node_modules/example/package.json']);
});

test('legacy typesVersions wildcard aliases resolve to their declaration files', () => {
  const f = fixture({ api: { legacy: '1.0.0' } });
  f.pkg('legacy', { types: 'index.d.ts', typesVersions: { '*': { 'feature/*': ['types/*.d.ts'] } } }, {
    'index.d.ts': 'export interface Root {}', 'types/extra.d.ts': 'export interface Extra {}',
  });
  succeeds(f.run('prepare'));
  const config = JSON.parse(fs.readFileSync(path.join(f.build, 'jaspring/typescript/types/tsconfig.jnpm.json')));
  assert.ok(config.compilerOptions.paths['legacy/feature/extra']);
});

test('missing optional dependencies do not invalidate a browser package', () => {
  const f = fixture({ implementation: { example: '1.0.0' } });
  f.pkg('example', { main: 'index.js', dependencies: { optional: '*' }, optionalDependencies: { optional: '*' } }, {
    'index.js': 'module.exports = 1;',
  });
  f.put('src/main/typescript/page.ts', 'import value from "example"; console.log(value);');
  succeeds(f.run('prepare'));
  succeeds(f.bundle());
});

test('CSS, JSON, assets and dynamic imports are emitted with hashes and stale chunks disappear', () => {
  const f = fixture();
  f.put('src/main/typescript/page.ts', 'import "./style.css"; import data from "./data.json"; import image from "./image.svg"; globalThis.lazy = () => import("./lib/lazy.ts"); console.log(data, image);');
  f.put('src/main/typescript/style.css', 'body { background: url("./image.svg"); cursor: url("data:image/png;base64,AA=="), auto; }');
  f.put('src/main/typescript/data.json', '{"count":3}');
  f.put('src/main/typescript/image.svg', '<svg xmlns="http://www.w3.org/2000/svg"/>');
  f.put('src/main/typescript/lib/lazy.ts', 'export const lazy = 1;');
  succeeds(f.bundle());
  const first = JSON.parse(fs.readFileSync(path.join(f.build, 'manifest.json')));
  assert.equal(first.styles.length, 1);
  assert.ok(first.assets.some(file => file.output.endsWith('.svg')));
  assert.ok(first.assets.some(file => file.output.startsWith('chunks/')));
  f.put('src/main/typescript/page.ts', 'console.log("changed");');
  succeeds(f.bundle());
  const second = JSON.parse(fs.readFileSync(path.join(f.build, 'manifest.json')));
  assert.notEqual(first.entries[0], second.entries[0]);
  assert.ok(!fs.existsSync(path.join(f.build, 'js', first.entries[0])));
});

test('custom ESLint config resolves plugins from build and lint-server uses unsaved text', () => {
  const f = fixture();
  f.put('eslint.config.mjs', 'import js from "@eslint/js"; export default [{...js.configs.recommended, files:["**/*.ts"], rules:{"no-unused-vars":"error",semi:["error","always"]}}];');
  f.put('src/main/typescript/page.ts', 'export const saved = 1;');
  succeeds(f.run('lint'));
  const request = JSON.stringify({ id: 42, file: path.join(f.project, 'src/main/typescript/page.ts'), text: 'const unused = 1\n' }) + '\n';
  const result = spawnSync(process.execPath, [bridge, 'lint-server', path.join(f.build, 'jnpm/config.json')],
    { cwd: f.project, input: request, encoding: 'utf8', timeout: 30000 });
  succeeds(result);
  const response = JSON.parse(result.stdout.trim());
  assert.equal(response.id, 42);
  assert.ok(response.messages.some(message => message.ruleId === 'no-unused-vars'));
  assert.ok(response.messages.some(message => message.fix));
  assert.equal(fs.readFileSync(path.join(f.project, 'src/main/typescript/page.ts'), 'utf8'), 'export const saved = 1;');
});
