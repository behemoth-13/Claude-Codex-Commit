package com.bocman.claudecommit

import com.intellij.openapi.application.ApplicationManager

internal object PluginFlavor {
    val provider = CliProvider.CLAUDE

    fun settings(): ClaudeCommitSettings =
        ApplicationManager.getApplication().getService(ClaudeCommitSettings::class.java)
}
