package com.bocman.claudecommit

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

internal object PluginFlavor {
    val provider = CliProvider.CODEX

    fun settings(): ClaudeCommitSettings =
        ApplicationManager.getApplication().getService(CodexCommitSettings::class.java)
}

@State(name = "CodexCommitSettings", storages = [Storage("codex-commit.xml")])
class CodexCommitSettings : ClaudeCommitSettings()
