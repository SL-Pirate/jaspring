# compat

`extract-r3-cases.py` turns Angular's `packages/compiler/test/render3/r3_template_transform_spec.ts` (branch `21.2.x`) into
`jaspring-core/src/test/resources/jaspring/compat/r3-template-transform.json`. `R3TemplateTransformCompatTest` replays every case
through the bundled compiler and the Java converter, so the Java AST is checked against Angular's own expectations.

```
curl -sL https://raw.githubusercontent.com/angular/angular/21.2.x/packages/compiler/test/render3/r3_template_transform_spec.ts -o /tmp/spec.ts
python3 tools/compat/extract-r3-cases.py /tmp/spec.ts jaspring-core/src/test/resources/jaspring/compat/r3-template-transform.json
```
