import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, join } from "node:path";

const require = createRequire(import.meta.url);

/// Resolves the virtual module `jaspring:typescript-libs` to the lib declaration files the compiler needs,
/// starting from the libs in the fixed option table and following `/// <reference lib="..." />` transitively.
/// Computed from the installed TypeScript so a version bump cannot leave a lib behind.
export default function typescriptLibs(roots) {
  return {
    name: "jaspring-typescript-libs",
    setup(build) {
      build.onResolve({ filter: /^jaspring:typescript-libs$/ }, (args) => ({
        path: args.path,
        namespace: "jaspring-typescript-libs",
      }));
      build.onLoad(
        { filter: /.*/, namespace: "jaspring-typescript-libs" },
        () => {
          const libDir = join(
            dirname(require.resolve("typescript/package.json")),
            "lib",
          );
          const libs = {};
          const queue = [...roots];
          while (queue.length) {
            const file = `lib.${queue.shift()}.d.ts`;
            if (libs[file]) continue;
            const text = readFileSync(join(libDir, file), "utf8");
            libs[file] = text;
            for (const match of text.matchAll(
              /\/\/\/\s*<reference\s+lib="([^"]+)"\s*\/>/g,
            ))
              queue.push(match[1]);
          }
          return {
            contents: `export const libs = ${JSON.stringify(libs)};`,
            loader: "js",
          };
        },
      );
    },
  };
}
