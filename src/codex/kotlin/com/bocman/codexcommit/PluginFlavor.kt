package com.bocman.codexcommit

import com.bocman.commitcore.CliProvider
import com.intellij.openapi.application.ApplicationManager

internal object PluginFlavor {
    val provider: CliProvider = CodexCliProvider

    fun settings(): CodexCommitSettings =
        ApplicationManager.getApplication().getService(CodexCommitSettings::class.java)
}
