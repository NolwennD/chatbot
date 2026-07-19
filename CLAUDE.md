# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Spring Boot application (Java 25, Spring Boot 4) that connects to a Twitch channel's chat and replies to `!commands`. Command→response pairs live in [commands.txt](commands.txt) (`!command=réponse`, one per line, `#` and blank lines ignored). The file is re-read on every command, so edits take effect without a restart. Scaffolded by [Seed4J](https://github.com/seed4j/seed4j); the `.seed4j/` modules record what was generated.

## Commands

Maven is driven through the wrapper (`./mvnw`); `pnpm` only powers formatting/git hooks.

- **Run the app**: `./mvnw` — the default goal is `spring-boot:run`. Requires `TWITCH_CHANNEL`, `TWITCH_BOT_USERNAME`, `TWITCH_OAUTH_TOKEN` env vars (see [README.md](README.md) for how to obtain the OAuth token). Serves on http://localhost:8080.
- **Unit tests** (Surefire): `./mvnw test` — excludes `*IT*` and `*CucumberTest*`.
- **Single test**: `./mvnw test -Dtest=HexagonalArchTest` or `-Dtest=HexagonalArchTest#shouldNotDependOnOutside`.
- **Full build incl. integration/Cucumber tests + coverage gate**: `./mvnw verify` — Failsafe runs `*IT*` and `*CucumberTest*`; JaCoCo `check` then enforces coverage.
- **Format** (Java, XML, Gherkin, package.json via Prettier): `pnpm prettier:format`; check-only: `pnpm prettier:check`. A Husky pre-commit hook runs lint-staged. Run `pnpm install` once after cloning.

Checkstyle runs automatically in the `validate` phase (config: [checkstyle.xml](checkstyle.xml)) and fails the build on violation — it also lints test sources.

### Coverage gate is strict

JaCoCo's `check` (at `verify`) allows **zero missed lines and zero missed branches per class**. New production code effectively needs full line+branch coverage or the build fails. Annotate genuinely untestable code with `@ExcludeFromGeneratedCodeCoverage` (in `shared.generation.domain`) rather than lowering the bar.

## Architecture

Hexagonal architecture, "application service" flavor (full rationale in [documentation/hexagonal-architecture.md](documentation/hexagonal-architecture.md)). The rules below are **enforced at test time by ArchUnit** ([HexagonalArchTest.java](src/test/java/fr/craft/chatbot/HexagonalArchTest.java)) — violating them breaks the build, so understand them before moving code.

Root package: `fr.craft.chatbot`. Code is organized into three kinds of top-level package, each identified by an annotation on its `package-info.java`:

- **Bounded contexts** (`@BusinessContext`) — one hexagon per business need. Currently only `command`. A context may not depend on another context's `domain`.
- **Shared kernels** (`@SharedKernel`) — reusable code under `shared/` (e.g. `shared.error` assertions, `shared.collection`, `shared.generation`). Everything under `shared/` must be a shared kernel.
- **Wire** (`wire/`) — cross-cutting framework config (e.g. `wire.security` CORS). Must not depend on any business context, and no business context may depend on it. Wire classes must be package-private.

Within a context, layers and their dependency rules:

- `domain/` — business model + **ports** (interfaces). Depends on nothing but other domains, shared kernels, and a small allowlist (`java.*`, Apache Commons, SLF4J). Model uses type-driven design: business concepts are `record`s validated in their compact constructor via `Assert.field(...)` (see `ChatMessage`, `CommandName`, `CommandResponse`).
- `application/` — orchestration only, **no business rules**. `@Service` classes wire ports together (see `HandleChatMessageService`). May not depend on `infrastructure`.
- `infrastructure/primary/` — driving adapters (things that call into the domain, e.g. the Twitch message listener). Must not depend on `secondary`. Spring `@Controller`s must be package-private.
- `infrastructure/secondary/` — driven adapters implementing domain ports (e.g. `FileCommandRepository` reads `commands.txt`, `TwitchChatMessagePublisher` sends replies). Must not depend on `application` or on its own context's `primary`.

The current flow: `TwitchChatMessageListener` (primary) subscribes to Twitch events via a facade → `HandleChatMessageService` (application) parses the `CommandName`, looks it up through the `CommandRepository` port, and publishes any match through the `ChatMessagePublisher` port.

**Cross-context communication**: a secondary adapter in context A calls a primary adapter in context B whose class name starts with `Java` (convention enforced by ArchUnit). Don't reach into another context's domain directly.

## Test-Driven Development

Write code test-first, following Kent Beck's process (_TDD By Example_; "Canon TDD", 2023). It isn't optional polish here: the strict per-class coverage gate above is satisfied naturally when code is written to make a failing test pass, and painful to retrofit afterwards.

**Two rules** drive the whole cycle:

1. Write production code only to make a failing test pass.
2. Remove duplication — in the code _and_ in the tests.

**The loop** — repeat until the test list is empty:

1. **Test list** — Before coding, jot down the behaviours and edge cases you need to cover (a scratch list or `// TODO`s). When a new case occurs to you mid-cycle, add it to the list instead of chasing it immediately.
2. **Red** — Turn _one_ item into a concrete, runnable test and watch it fail. Write it against the interface you _wish_ existed — this is where you design the record/port's API.
3. **Green** — Do the fastest thing that makes that test (and every existing test) pass, even if ugly. Reach, in increasing order of effort, for: **Obvious Implementation** (just type it when it's trivial), **Fake It** (return a constant, then generalise), or **Triangulation** (add a second example to force the generalisation).
4. **Refactor** — With the bar green, remove duplication and clean the design while tests stay green. This step is optional — do it when the code asks for it.

**Take small steps.** Larger steps are fine when you're confident; shrink them when a step surprises you (unexpected red, a hard-to-write test). A test that's hard to write is feedback about the design — often a domain concept wants extracting or a port is missing.

This maps onto the architecture: start in the `domain` (a `record` + its `Assert` validation, or a port interface), drive the `application` service against fake/in-memory port implementations, then write the `secondary`/`primary` adapters last.

## Creating & refactoring Java code (jdtls)

Create Java types and rename/refactor existing code through **jdtls** (the Eclipse JDT Language Server), not by writing or find-and-replacing text. jdtls works on the AST and project model, so package declarations, references, and imports stay correct across the whole codebase. Extraction/rename is the Refactor step of the TDD loop above.

A committed helper drives jdtls headlessly: [.claude/tools/jdtls-driver.py](.claude/tools/jdtls-driver.py). `jdtls` is on `PATH`; the first run per session spends ~60s importing the Maven project, then a per-project workspace under `~/.cache/jdtls-ws/` stays warm. Add `--dry-run` to preview edits without writing. Positions are 1-based (line:col as shown in an editor).

```bash
# create a type — package from the path, name from the filename
python3 .claude/tools/jdtls-driver.py create src/main/java/fr/craft/chatbot/command/domain/Foo.java record   # or class | interface | enum

# rename a symbol and every reference to it, project-wide
python3 .claude/tools/jdtls-driver.py rename --token <file> <newName> <identifier>
python3 .claude/tools/jdtls-driver.py rename <file> <line> <col> <newName>

# extract a selected expression / statements (jdtls names methods/fields with a default — rename after)
python3 .claude/tools/jdtls-driver.py extract-var    <file> <sLine> <sCol> <eLine> <eCol>
python3 .claude/tools/jdtls-driver.py extract-const  <file> <sLine> <sCol> <eLine> <eCol>
python3 .claude/tools/jdtls-driver.py extract-method <file> <sLine> <sCol> <eLine> <eCol>
python3 .claude/tools/jdtls-driver.py extract-field  <file> <sLine> <sCol> <eLine> <eCol>

# inline a local variable or method; remove unused imports and sort
python3 .claude/tools/jdtls-driver.py inline <file> <line> <col>
python3 .claude/tools/jdtls-driver.py organize-imports <file>
```

Use it for **every new class/interface/record/enum file**, for renames, and for extractions/inlines. Fall back to manual edits only for what it can't express.

### Optional: more actions via the vscode-java extension

Everything above runs on the **base jdtls — no extension needed**. Loading the Red Hat **vscode-java** extension bundles gives jdtls _additional_ delegate commands (run/debug tests, Lombok, more source and quick-fix actions) — but **not** the create/rename/extract/inline actions, which already work. To enable it: download the `redhat.java` extension (a `.vsix` from [Open VSX](https://open-vsx.org/extension/redhat/java)), unzip it, and pass the JARs under `extension/server/` to jdtls as `initializationOptions.bundles` in the driver's `initialize` call.

## Test conventions

Tag every test with a category annotation (drives `@DisplayNameGeneration` and Spring wiring): `@UnitTest`, `@ComponentTest`, or `@IntegrationTest`. `@IntegrationTest` boots the full context with the `test` profile and a `TwitchTestClientConfiguration` so no real Twitch connection is made. Integration test classes must be named `*IT`; Cucumber suites `*CucumberTest`. `.feature` files go in `src/test/features/` (see [documentation/cucumber.md](documentation/cucumber.md)). Beyond `HexagonalArchTest`, other ArchUnit suites enforce annotation usage and `equals`/`hashCode` conventions.

## Continuous Integration

[.github/workflows/ci.yml](.github/workflows/ci.yml) runs `pnpm prettier:check` and `./mvnw verify` on every PR and on push to `main`. [renovate.json](renovate.json) (extends `config:recommended`) keeps dependencies current automatically, including this workflow's own pinned action versions — its `github-actions` manager is enabled by default.

When pinning a new GitHub Action version (`uses: owner/repo@vN`) or any other fast-moving external tool, verify the actual current major on its real releases page (e.g. via web search/fetch) instead of guessing from memory — these move faster than a model's training data tracks, and getting it wrong at authoring time is exactly the kind of drift Renovate can't fix retroactively before the first PR merges.
