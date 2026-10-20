# Project guide

## Scope

This IntelliJ Platform plugin generates Git commit messages through a locally
installed CLI. Claude Commit is the default flavor; `-Pflavor=codex` builds
Codex Commit. Keep provider and branding changes scoped to the selected flavor.

Keep replies brief and use the user's language. Keep code, UI text, and repository
documentation in English, following existing conventions.

## Project map

Sources: `src/main/kotlin/com/bocman/claudecommit/`.

- `GenerateWithClaudeAction.kt`: commit-toolbar action, branch lookup and IDE patch generation.
- `CommitMessageGenerator.kt`: shared settings, prompt expansion and generation entry point.
- `CommitCli.kt` / `CliProvider.kt`: CLI discovery, provider arguments/results, subprocess execution and cancellation.
- `ClaudeCommitHandler.kt`: commit-options button; uses Git helpers from
  `GenerateWithClaudeAction` and the shared `CommitMessageGenerator`.
- `ClaudeCommitHandlerFactory.kt`: registers the commit handler.
- `ClaudeCommitSettings.kt`: application-level persistent settings, effort levels,
  and default prompt. Storage: `claude-commit.xml`.
- `ClaudeCommitConfigurable.kt`: Settings → Tools → Claude Commit.
- `src/{claude,codex}/`: flavor-specific provider binding, plugin descriptors and icons.
  Codex registers its own settings service and stores settings in `codex-commit.xml`.

## Build and verification

- Gradle wrapper: 8.14.3; Kotlin: 2.1.0; IntelliJ Platform Gradle plugin: 2.7.1.
- Compilation targets JVM 21 and IntelliJ IDEA Community 2025.1.4.1 with Git4Idea.
- `./gradlew buildPlugin`: build the distributable ZIP in `build/distributions/`.
- Add `-Pflavor=codex` to build, test or run Codex Commit. Intermediate output
  and sandbox IDEs are isolated in `build/claude/` and `build/codex/`.
- `./gradlew runIde`: launch a sandbox IDE.
- `./gradlew test`: run tests under `src/test/kotlin/` with IntelliJ Platform and
  temporary Git repositories. Run Gradle with JDK 21.
- For behavior changes, build and manually verify the affected flow in the
  sandbox IDE. Check both generation entry points when changing shared behavior.
  Relevant cases include selected files, empty selection, settings persistence,
  missing/invalid CLI, CLI failure, and cancellation.
- Documentation-only changes require diff review, not launching an IDE or Claude.
- Report exactly which checks ran and which remain unverified.

## Implementation conventions

- Preserve selected `ChangeListChange` objects and use the IDE patch builder with
  `honorExcludedFromCommit=true`. The commit UI selection includes individual
  hunks/lines and is not the same as the Git index. Never fall back to a whole-file
  or repository diff when the selection produces an empty patch.
- Keep blocking Git/Claude work in background tasks and UI updates on the EDT.
- Preserve cancellation, subprocess stream draining, and temporary-file cleanup.
- Keep process arguments separate through `ProcessBuilder`; avoid shell command
  interpolation. The prompt is supplied via redirected standard input.
- Preserve `{BRANCH}` and `{DIFF}` placeholders and persisted settings compatibility.
- The plugin generates text; it does not itself create commits.
- Do not add credentials or generated build/sandbox artifacts to version control.

## Known constraints to revisit when relevant

- Compatibility declarations conflict: README, `plugin.xml`, and `patchPluginXml`
  claim 2023.2 / build 232, while compilation uses IDEA 2025.1.4.1, JVM 21, and
  `pluginConfiguration.ideaVersion` sets build 251. Older IDE support is unverified.
- `platformVersion` and `platformType` in `gradle.properties` are currently not
  used by the hardcoded platform dependency in `build.gradle.kts`.
- Branch lookup currently uses the first repository in the project; do not
  assume correct multi-root branch context.
- CLI discovery contains Unix-specific paths; Windows support is unverified.

## Agent tooling

Follow user-level tool routing: jdtls owns Java diagnostics and semantic queries;
JetBrains MCP owns rename, formatting, build, and project structure. Read the
user's corresponding `rules_conditional` file before using either integration.
Use native tools when the owning integration is unavailable or incomplete.

Keep edits scoped to the request. Do not commit or publish unless asked. For
commits or MR descriptions authored by Codex, include:

`Assisted-by: Codex <noreply@openai.com>`
