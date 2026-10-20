package com.bocman.claudecommit

import com.bocman.commitcore.CliProvider
import com.intellij.openapi.application.ApplicationManager

internal object PluginFlavor {
    val provider: CliProvider = ClaudeCliProvider

    fun settings(): ClaudeCommitSettings =
        ApplicationManager.getApplication().getService(ClaudeCommitSettings::class.java)
}
