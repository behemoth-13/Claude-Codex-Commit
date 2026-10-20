package com.bocman.commitcore

import com.intellij.openapi.progress.ProgressIndicator

/** Both commit UI entry points use the same settings, prompt and CLI path. */
internal object CommitMessageGenerator {
    fun generate(branch: String, diff: String, indicator: ProgressIndicator): String {
        val provider = PluginFlavor.provider
        val settings = CommitSettings.getInstance()
        val cli = CommitCli(provider)
        val executable = cli.find(settings.executablePath)
            ?: throw RuntimeException(
                "${provider.cliName} CLI not found.\n" +
                    "Install ${provider.cliName} or configure its path under Settings → Tools → ${provider.pluginName}."
            )
        val prompt = settings.promptTemplate
            .replace("{BRANCH}", branch)
            .replace("{DIFF}", diff)
        return cli.generate(executable, prompt, settings.effortLevel.cliValue, indicator)
    }
}
