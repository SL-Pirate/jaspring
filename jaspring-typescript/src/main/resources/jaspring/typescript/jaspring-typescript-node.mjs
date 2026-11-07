// Node host for the bundle the GraalJS host runs: one JSON request on stdin, one JSON result on stdout.
// Used by jaspring-typescript's NodeBundleHost; see tools/js-bundle/src/typescript.mjs for the format.
import { run } from "./jaspring-typescript.mjs";

let input = "";
process.stdin.setEncoding("utf8");
for await (const chunk of process.stdin) input += chunk;
process.stdout.write(run(input));
