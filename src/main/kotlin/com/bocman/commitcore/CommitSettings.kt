package com.bocman.commitcore

/** Common settings API; each flavor owns its persisted state and executable path. */
abstract class CommitSettings {

    enum class EffortLevel(val cliValue: String, val displayName: String) {
        LOW("low", "Low"),
        MEDIUM("medium", "Medium"),
        HIGH("high", "High"),
        XHIGH("xhigh", "Extra High"),
        MAX("max", "Max");

        override fun toString() = displayName
    }

    /** Shared XML fields, inherited by the flavor's mutable state bean. */
    open class State {
        @JvmField
        var promptTemplate: String = DEFAULT_PROMPT
        @JvmField
        var effortLevel: String = EffortLevel.LOW.cliValue
    }

    protected abstract val settingsState: State

    abstract var executablePath: String

    var promptTemplate: String
        get() = settingsState.promptTemplate
        set(v) {
            settingsState.promptTemplate = v
        }

    var effortLevel: EffortLevel
        get() = PluginFlavor.provider.effortLevels.firstOrNull { it.cliValue == settingsState.effortLevel }
            ?: EffortLevel.LOW
        set(v) {
            settingsState.effortLevel = v.cliValue
        }

    companion object {
        fun getInstance(): CommitSettings =
            PluginFlavor.settings()

        /**
         * Default prompt mirrors the JetBrains AI "Commit Message Generation" built-in action.
         * Placeholders: {BRANCH} and {DIFF}.
         */
        val DEFAULT_PROMPT: String = """
You are an expert software developer writing a git commit message.

Branch: {BRANCH}

Staged changes:
```diff
{DIFF}
```

Rules:
- Subject line: imperative mood, ≤72 characters (e.g. "Fix null pointer on login screen")
- If the subject alone is not enough, add ONE blank line then a short body explaining WHY
- Reference the JIRA ticket from the branch name when present (e.g. ANDROID-12345)
- Be specific; avoid vague words like "update" or "fix stuff"
- Output ONLY the commit message — no markdown, no code blocks, no explanations
        """.trimIndent()
    }
}
