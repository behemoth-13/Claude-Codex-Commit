package com.bocman.claudecommit

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows

/** Settings panel shared by both plugin flavors. */
class ClaudeCommitConfigurable : BoundConfigurable(PluginFlavor.provider.pluginName) {

    private val settings = ClaudeCommitSettings.getInstance()

    override fun createPanel(): DialogPanel = panel {
        row("${PluginFlavor.provider.displayName} executable:") {
            textField()
                .bindText(settings::executablePath)
                .comment("Leave empty to auto-detect. Example: /opt/homebrew/bin/${PluginFlavor.provider.executable}")
                .align(AlignX.FILL)
        }
        row("Effort level:") {
            comboBox(PluginFlavor.provider.effortLevels)
                .bindItem(
                    { settings.effortLevel },
                    { settings.effortLevel = it ?: ClaudeCommitSettings.EffortLevel.LOW }
                )
                .comment("Controls how much reasoning ${PluginFlavor.provider.displayName} applies. Low is fastest.")
        }
        collapsibleGroup("Prompt Template") {
            row {
                textArea()
                    .bindText(settings::promptTemplate)
                    .rows(18)
                    .align(AlignX.FILL)
                    .comment("Use {BRANCH} and {DIFF} as placeholders for branch name and git diff.")
            }.resizableRow()
        }
        row {
            button("Reset To Default") {
                settings.promptTemplate = ClaudeCommitSettings.Companion.DEFAULT_PROMPT
                // recreate panel to refresh the text area
                reset()
            }
        }
    }
}
