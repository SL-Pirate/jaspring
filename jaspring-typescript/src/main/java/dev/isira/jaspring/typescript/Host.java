package dev.isira.jaspring.typescript;

/// Where the bundled compiler runs. `GRAALJS` needs nothing installed; `NODE` runs the same bundle in a Node 20+
/// process, which starts faster and compiles faster. Both emit byte-identical JavaScript.
public enum Host {
    GRAALJS,
    NODE
}
