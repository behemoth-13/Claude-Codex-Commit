# Codex Commit & Claude Commit

[![Install Codex Commit](https://img.shields.io/badge/Marketplace-Install_Codex_Commit-111827?logo=jetbrains)](https://plugins.jetbrains.com/plugin/35355-codex-commit)
[![Install Claude Commit](https://img.shields.io/badge/Marketplace-Install_Claude_Commit-D97757?logo=jetbrains)](https://plugins.jetbrains.com/plugin/31445-claude-commit)

> Codex Commit is currently awaiting JetBrains Marketplace review. Its installation link will become available after approval.

Two IntelliJ Platform plugins that generate Git commit messages from your selected
changes using a locally installed AI CLI. This repository contains both plugins,
with shared commit selection, message generation, and settings UI.

| Icon                                                         | Plugin                           | CLI                                                                             | Build command                           |
|--------------------------------------------------------------|----------------------------------|---------------------------------------------------------------------------------|-----------------------------------------|
| ![Plugin Icon](src/codex/resources/META-INF/pluginIcon.svg)  | **Codex Commit** (default build) | [Codex CLI](https://learn.chatgpt.com/docs/codex/cli#getting-started) (`codex`) | `./gradlew buildPlugin`                 |
| ![Plugin Icon](src/claude/resources/META-INF/pluginIcon.svg) | **Claude Commit**                | [Claude Code](https://claude.ai/download) (`claude`)                            | `./gradlew buildPlugin -Pflavor=claude` |

Install the plugin matching the CLI you use, or install both. Each plugin has its
own settings and generation action. They fill in the commit message for you to
review and edit; they do not create commits.

The screenshots below show Claude Commit; Codex Commit uses the same workflow
with Codex-specific labels and settings.

<img width="1200" height="761" alt="Screenshot 2026-04-24 at 20 16 52" src="https://github.com/user-attachments/assets/2b72fd41-0b3b-4bb9-a903-3f535fef88b6" />
<img width="1213" height="821" alt="image" src="https://github.com/user-attachments/assets/1c4b80e8-8eb4-4bd4-a12a-81b7c9458507" />


## How it works

1. Open the Git commit dialog (`⌘0` / `Alt+0`)
2. Check the files or individual changes you want to commit
3. Click **Generate with Codex** or **Generate with Claude** in the toolbar (or the button in the commit options strip)
4. The plugin reads the branch name and diff of the selected changes, sends them to its corresponding CLI, and fills in the commit message. Unchecked hunks and lines within a file are excluded.

The plugin uses your CLI's existing authentication; it does not store credentials.
Sign in to the corresponding CLI before generating a message.

## Requirements

- IntelliJ-based IDE 2025.1 or later (build 251+) with Git support.
- The CLI matching your plugin (`codex` or `claude`), installed and authenticated.
  It must be on `$PATH`, in a common installation location, or configured explicitly in settings.
- Codex CLI must support `exec`, `--ephemeral`, `--sandbox`, and `--output-last-message`.

The build target is IntelliJ IDEA Community 2025.1.4.1 with JVM 21.
The minimum declared IDE version is 2025.1 (build 251); older IDE versions are
unsupported. Other IDE products and newer releases require separate compatibility
verification; check the Marketplace page for your IDE's available versions.

## Installation

Install from the JetBrains Marketplace:

- [Codex Commit](https://plugins.jetbrains.com/plugin/35355-codex-commit) — awaiting Marketplace review; installation will be available after approval.
- [Claude Commit](https://plugins.jetbrains.com/plugin/31445-claude-commit)

For manual installation, download the matching plugin ZIP from
[GitHub Releases](https://github.com/behemoth-13/Claude-Codex-Commit/releases)
and use **Settings → Plugins → Install Plugin from Disk**. Each release includes
notes about its requirements and Marketplace availability.

Alternatively, build locally:

```bash
# Codex Commit (default):
./gradlew buildPlugin
# Claude Commit:
./gradlew buildPlugin -Pflavor=claude
```

Then install the ZIP from `build/distributions/` via **Settings → Plugins → Install Plugin from Disk**.
The ZIPs are named `Codex-Commit-<version>.zip` and `Claude-Commit-<version>.zip`.
Each flavor has its own plugin ID, action ID, and settings file, so both can be installed together.
Codex is the default build flavor. Use `-Pflavor=claude` for Claude Commit, which retains its existing settings.

## Settings

Open **Settings → Tools → Codex Commit** or **Settings → Tools → Claude Commit**,
depending on the installed plugin.

| Setting | Description |
|---|---|
| Codex / Claude executable | Path to the corresponding CLI binary. Leave empty to auto-detect from common locations and `$PATH`. |
| Model | Choose a model or type a custom model name. **Default (CLI configuration)** or an empty field omits `--model`, preserving the CLI's configured default. Models load from the CLI in the background; **Refresh Models** reloads them using the executable path currently entered. If discovery fails, built-in suggestions remain available. |
| Effort level | Codex: Low → Medium → High → Extra High. Claude: Low → Medium → High → Extra High → Max. Supported effort levels depend on the CLI's selected model. |
| Prompt template | The prompt sent to the CLI. Use `{BRANCH}` and `{DIFF}` as placeholders. |

Codex runs non-interactively with a read-only sandbox, approval prompts disabled,
and an ephemeral session in a temporary working directory. The prompt is passed through
stdin; only the final response file is used as the commit message. Repository-local
instructions are not loaded from the project directory. Authentication and the default model
come from your Codex CLI configuration; selecting a model in plugin settings overrides only the model.

Model discovery uses Codex App Server's `model/list` or Claude Code's initialization
response, without sending a prompt or starting a generation. Requests time out after
10 seconds and are cancelled when settings close or the executable path changes.
Older CLI versions that do not support discovery use the built-in suggestions.

## Building

```bash
./gradlew runIde      # launch a sandbox IDE with Codex Commit loaded
./gradlew buildPlugin # produce the Codex Commit distributable ZIP
./gradlew test        # run Codex and shared IntelliJ Platform integration tests

./gradlew runIde -Pflavor=claude
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

## Feedback

[Report a bug](https://github.com/behemoth-13/Claude-Codex-Commit/issues/new?template=bug_report.yml)
or [suggest a feature](https://github.com/behemoth-13/Claude-Codex-Commit/issues/new?template=feature_request.yml).
Include the plugin name, plugin version, IDE version, and CLI version when reporting a bug.

## License

MIT
