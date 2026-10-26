# Jaspring for IntelliJ IDEA

A standalone Java-aware plugin for Jaspring templates, targeting IntelliJ IDEA 2026.2 (build 262).
It depends on IntelliJ's Java and XML/HTML support. Angular, JavaScript/TypeScript, Node.js and a language server are not required.

## Install

Build from the repository root with JDK 25:

```sh
./gradlew -p tools/intellij-plugin buildPlugin
```

In IntelliJ IDEA, open **Settings → Plugins → gear menu → Install Plugin from Disk**, select
`tools/intellij-plugin/build/distributions/jaspring-intellij-0.1.0.zip`, and restart when prompted.

The plugin recognizes `.component.html` files in modules with Jaspring on their source/classpath roots.
Java-aware features activate when a Java `@Component(templateUrl = "...")` owns the file; there is no
configuration file or generated TypeScript to maintain. Other `.html` filenames referenced by `templateUrl`
also receive completion and references through ordinary HTML support. Use `.component.html` to get
the Jaspring HTML parser, which keeps comparisons and HTML-looking strings inside expressions from being parsed as tags.

Java and resources may be in separate source roots, as in this repository:

```text
src/main/java/demo/cards/Card.java
src/main/resources/demo/cards/card.component.html
```

Relative resource paths follow the runtime's package rules, including `./`, `../`, and classpath-absolute `/...` paths.
IntelliJ must have imported the Gradle/Maven project and finished indexing for Java code insight to work.

## Features

- Completion and Go to Declaration for component fields (including private fields), public methods,
  JavaBean properties, inherited members, and record accessors.
- Type-aware member completion through signal calls, method results, and generic iterable/array elements.
- Scoped `@for` variables and aliases, `@if` aliases, and `@let` values; block keyword highlighting and completion.
- Component tag, input/output and model binding completion, including input/output aliases.
  Simple element, attribute, compound element/attribute, and comma-separated selectors are recognized.
- Navigation from binding names and custom pipes to Java declarations; built-in pipe name completion.
- Java rename/find-usages integration for template references.
- Navigation from `templateUrl` and `styleUrl` strings, a Java class gutter icon, and **Navigate → Related Symbol**
  for switching between a component and its resources.
- An inspection for missing template and stylesheet resources.
- HTML language injection into inline `template` strings and text blocks. Inline `styles` receive CSS injection when
  the IDE's CSS language is available.

Templates remain HTML-compatible files, so existing HTML/CSS tooling can participate. The plugin does not change Tailwind configuration.

## Current limits

This is an initial code-insight implementation, not an IDE port of the entire Angular compiler. It does not provide full
template type checking, Angular grammar diagnostics, overload selection, or a control-flow-aware formatter.
Arrow parameter types, ICU expressions, structural-directive microsyntax locals, template-reference variables (`#ref`),
and complex CSS selectors such as `:not(...)` are not yet modeled. Unknown or dynamic expressions are left unresolved
rather than diagnosed as errors. Local-variable rename is not a supported refactoring. Inline injection covers literal
strings and text blocks, not concatenated templates.

The runtime's Angular compiler remains the authority for template validation. Compatibility is deliberately restricted to
the tested IntelliJ 262 API line; newer IDE lines should be verified before widening the plugin's version range.

## Develop and verify

The plugin has an independent Gradle build so framework builds do not download an IDE SDK.
It uses the repository's existing wrapper:

```sh
./gradlew -p tools/intellij-plugin test buildPlugin verifyPluginStructure
./gradlew -p tools/intellij-plugin runIde
```

To use an already installed IntelliJ IDEA 2026.2 SDK instead of downloading it, add
`-PplatformPath=/absolute/path/to/intellij-idea` to either command.
`runIde` launches a separate sandbox; it does not install into your everyday IDE.

Tests use IntelliJ's Java code-insight fixtures and their JUnit test stack. They exercise actual completion, references,
refactoring, language injection and project resource roots. The test IDE loads Jaspring and its Java/XML dependencies
without the Angular or JavaScript plugins.

## Reused code

`BindingName.java` adapts the small binding-name classifier from
[JetBrains' Angular plugin](https://github.com/JetBrains/intellij-plugins/blob/master/Angular/angular-backend/src/org/angular2/lang/html/parser/Angular2AttributeNameParser.kt).
Its copyright, Apache 2.0 license and modifications are recorded in `src/main/resources/META-INF/NOTICE` and
`src/main/resources/licenses/Apache-2.0.txt`, which are included in the plugin archive.
The remaining implementation is Jaspring code under the repository's MIT license.
