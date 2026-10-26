# Personal coding preferences

These are my default preferences, based on my Java projects and how I work. Apply them to new code and changes. Explicit task instructions and more specific repository guidance take precedence. Respect an existing project's toolchain, formatter and architecture; do not rewrite unrelated code to enforce this guide.

## Java style

- Use modern Java supported by the project's configured toolchain. My reference project uses Java 25; do not upgrade another project's Java version without a reason.
- Use four spaces, same-line opening braces, and blank lines between logical steps. Follow the surrounding file's wrapping and import style. Avoid unrelated formatting changes.
- Prefer `final var` for local variables and enhanced-for loop variables that are not reassigned. Use `var` for reassigned locals. Use explicit types when inference is unavailable or when they clarify a generic contract or declaration.
- Keep fields `private final` where practical. Use the narrowest useful visibility; internal helpers and implementation classes need not be public.
- Prefer records for immutable data carriers, results and small domain values. Nest a record or enum when it belongs to one enclosing concept. Use ordinary classes when identity, mutation, persistence or lifecycle requires them.
- Use descriptive names, familiar domain terms, and verbs for methods. Prefer readable names over abbreviations, generic manager/helper names, or verbose enterprise terminology.
- Use guard clauses to reduce nesting. Braces are the normal default; short, obvious early returns or throws may stay on one line when that matches nearby code.
- Use streams for straightforward filtering, mapping and collection. Use loops for stateful algorithms, branching, bookkeeping and concurrency. Do not force an algorithm into a stream chain.
- Prefer `List.of`, `Set.of`, `Map.of` and `.toList()` for fixed/read-only values. Use mutable collections intentionally and `LinkedHashMap`/`LinkedHashSet` when encounter order matters. Copy collection inputs at ownership boundaries when immutability is part of the contract.
- Use text blocks for multiline prompts or payload templates, and `.formatted(...)` for readable interpolation. Use parameterized logging rather than concatenating log messages.
- Keep nullability deliberate. Document meaningful nullable fields and validate external inputs at boundaries. Follow existing `Optional` conventions rather than wrapping every nullable value or adding defensive checks everywhere.

## Comments and documentation

- Prefer Markdown JavaDoc comments with `///` where the toolchain supports them. Use `//` for local implementation notes.
- Explain intent, invariants, surprising decisions and the consequence of getting them wrong. Do not narrate obvious statements or add boilerplate comments to every method.
- Document ordering, ownership, nullability and failure behavior when callers depend on them. Keep comments proportionate to the complexity.
- Write plain, concise prose. Update documentation when behavior changes; remove stale claims instead of appending contradictory notes.

## Design and framework conventions

- Organize Spring Boot code by layer: controllers in `controllers`, services in `services`, repositories in `repositories`, models in `models`, configurations in `config`, and shared utilities in `util`. Mirror these packages in tests.
- Prefer small, cohesive classes with clear responsibilities. Keep transformations and decision logic pure where practical; separate them from network, database and storage work.
- Define services through documented interfaces and keep implementations under `services.impl` (with subpackages where useful). Inject interfaces rather than concrete implementations everywhere; use qualifiers when multiple implementations exist. Document contracts, ownership and failure behavior on the interfaces. Apply the same separation to other injectable application responsibilities where appropriate.
- In Spring projects, prefer constructor injection for service dependencies. Where Lombok is already used, use focused annotations such as `@RequiredArgsConstructor`, `@Slf4j`, `@Getter` and `@Setter` instead of generating equivalent boilerplate by hand.
- Keep database transactions short. Browser work, AI calls and remote I/O should not hold a transaction open. Follow existing entity, repository and migration conventions.
- Reuse the project's existing libraries and serializers. My reference project uses Gson for application wire DTOs and `@SerializedName` for external field names; that is a local convention, not a reason to replace another project's serializer.
- Preserve existing behavior outside the requested change, including stored identities, provenance, ordering and user review decisions. Keep comparison or presentation views separate from persisted source data.

## Defaults, external services and AI

- Choose sensible internal defaults. Do not expose a configuration option for every implementation detail. Internal batch sizing and safety margins should be automatic or hardcoded unless an actual operational need calls for configuration.
- Size batches from payloads and documented service limits, including encoding, framing and output headroom. An arbitrary item count or optimistic characters-per-token estimate is not enough. Keep meaningful existing operator controls distinct from internal safeguards.
- Keep prompts concise and explicit, especially for small models. More instructions do not automatically improve results. Prefer clearer inputs and a few necessary distinctions over accumulating exceptions and examples.
- Evaluate prompt or classification changes against representative cases, including real failures when available. Measure the behavior; do not claim accuracy from code inspection or one convenient example.
- Preserve answer-to-input mapping explicitly in batches. Isolate partial failures where possible. Bound retries and waits, respect cancellation, and report incomplete work honestly.
- Do not sacrifice useful coverage merely to minimize usage when I have said the service cost is negligible.

## Tests and verification

- Use the project's existing test stack. In my Java projects this is typically JUnit Jupiter, Mockito and plain assertions.
- Prefer descriptive camelCase test names that read as behavior, such as `missingAnswersDoNotDiscardValidSiblings`.
- Keep fixtures small and relevant. Use fakes for domain decisions and mocks at external boundaries. Assert observable behavior and important invariants rather than mirroring implementation details.
- Add regression cases for meaningful bugs, boundary conditions and changed contracts. Do not add ceremonial tests for trivial, reversible edits.
- Keep normal tests independent of live services. Gate live tests explicitly, run them when authorized, and use configured credentials privately. Never print or commit secrets, including in fixtures or reports.
- Run the checks appropriate to the change using the repository's wrapper/scripts. Distinguish passing, failed, skipped and incomplete checks. A stalled or unrun suite is not a passing suite.

## Working with me

- Investigate existing code and available evidence before asking me for information the project already contains.
- Make routine, reversible implementation decisions and finish the authorized work. Avoid repeated confirmation questions; ask when a material ambiguity or required permission prevents progress.
- Keep changes focused. Do not add dependencies, settings, abstractions or broad refactors without a concrete benefit to the task.
- Give concise progress updates during longer work. In the final response, lead with the result, mention meaningful validation, and state remaining limitations plainly.
- Favor maintainable, good-enough improvements backed by evidence. Do not turn a narrow optimization into a pursuit of theoretical perfection.
