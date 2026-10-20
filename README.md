# Claude Commit

An IntelliJ plugin that generates Git commit messages using a locally installed CLI.
Two build flavors share the same generation logic: **Claude Commit** uses
[Claude Code](https://claude.ai/download), and **Codex Commit** uses Codex CLI.

![Plugin Icon](src/claude/resources/META-INF/pluginIcon.svg)

<img width="1200" height="761" alt="Screenshot 2026-04-24 at 20 16 52" src="https://github.com/user-attachments/assets/2b72fd41-0b3b-4bb9-a903-3f535fef88b6" />
<img width="1200" height="761" alt="Screenshot 2026-04-24 at 20 17 45" src="https://github.com/user-attachments/assets/3d3e6d44-131a-4554-9861-639472634b58" />


## How it works

1. Open the Git commit dialog (`⌘0` / `Alt+0`)
2. Check the files or individual changes you want to commit
3. Click **Generate with Claude** or **Generate with Codex** in the toolbar (or the button in the commit options strip)
4. The plugin reads the branch name and diff of the selected changes, sends them to the selected flavor's CLI, and fills in the commit message. Unchecked hunks and lines within a file are excluded.

The plugin uses your CLI's existing authentication; it does not store credentials.
Sign in to the corresponding CLI before generating a message.

## Requirements

- IntelliJ-based IDE with Git support (compiled and tested against IDEA 2025.1).
- The CLI matching your flavor (`claude` or `codex`), installed and authenticated.
  It must be on `$PATH`, in a common installation location, or configured explicitly in settings.
- Codex CLI must support `exec`, `--ephemeral`, `--sandbox`, and `--output-last-message`.

The compilation target is currently IntelliJ IDEA 2025.1 with JVM 21.
Although the plugin metadata declares 2023.2 compatibility, older IDE versions remain unverified.

## Installation

Install from the JetBrains Marketplace, or build locally:

```bash
./gradlew buildPlugin
# Or build the Codex flavor:
./gradlew buildPlugin -Pflavor=codex
```

Then install the ZIP from `build/distributions/` via **Settings → Plugins → Install Plugin from Disk**.
The ZIPs are named `Claude-Commit-<version>.zip` and `Codex-Commit-<version>.zip`.
Each flavor has its own plugin ID, action ID, and settings file, so both can be installed together.
Claude remains the default flavor and retains its existing settings.

## Settings

**Settings → Tools → Claude Commit** or **Codex Commit**

| Setting | Description |
|---|---|
| Claude / Codex executable | Path to the corresponding CLI binary. Leave empty to auto-detect from common locations and `$PATH`. |
| Effort level | Claude: Low → Medium → High → Extra High → Max. Codex: Low → Medium → High → Extra High. Supported effort levels depend on the CLI's selected model. |
| Prompt template | The prompt sent to the CLI. Use `{BRANCH}` and `{DIFF}` as placeholders. |

Codex runs non-interactively with a read-only sandbox, approval prompts disabled,
and an ephemeral session in a temporary working directory. The prompt is passed through
stdin; only the final response file is used as the commit message. Repository-local
instructions are not loaded from the project directory. Model selection and authentication
come from your Codex CLI configuration.

## Building

```bash
./gradlew runIde      # launch a sandbox IDE with the plugin loaded
./gradlew buildPlugin # produce a distributable ZIP
./gradlew test        # run IntelliJ Platform integration tests

./gradlew runIde -Pflavor=codex
./gradlew test buildPlugin -Pflavor=claude
./gradlew test buildPlugin -Pflavor=codex
```

Use JDK 21 to run Gradle. Intermediate build output, test reports, and sandbox IDEs
are isolated under `build/claude/` and `build/codex/`; distributable ZIPs share
`build/distributions/`. Switching flavors does not require `clean`.
After moving classes between packages or source sets, run `clean` once for each
flavor to remove stale instrumented classes.

Shared UI, process execution, and settings APIs live in `com.bocman.commitcore`.
Each flavor supplies its CLI arguments, response parsing, supported effort levels,
working-directory policy, and persistent settings in `com.bocman.claudecommit` or
`com.bocman.codexcommit`, selected through a compile-time `PluginFlavor` type alias.
Provider-specific tests live under `src/<flavor>/test/kotlin` and run alongside
the shared tests. Each distributable includes only its own provider implementation.
Plugin IDs, settings component names, storage files, and executable-path XML keys
remain stable across package refactors.

## License

MIT
