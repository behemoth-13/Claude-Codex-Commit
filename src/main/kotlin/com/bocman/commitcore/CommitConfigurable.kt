package com.bocman.commitcore

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows
import com.intellij.util.concurrency.AppExecutorUtil
import javax.swing.JButton
import javax.swing.event.DocumentEvent

/** Settings panel shared by both plugin flavors. */
class CommitConfigurable : BoundConfigurable(PluginFlavor.provider.pluginName) {

    private val settings = CommitSettings.getInstance()
    private var modelLoad: EmptyProgressIndicator? = null

    override fun createPanel(): DialogPanel {
        val provider = PluginFlavor.provider
        val executableField = JBTextField(settings.executablePath)
        val selector = ModelSelector(provider.fallbackModels).apply { selectedModel = settings.model }
        val status = JBLabel()
        lateinit var refreshButton: JButton

        fun refreshModels() {
            modelLoad?.cancel()
            val indicator = EmptyProgressIndicator()
            modelLoad = indicator
            val path = executableField.text
            status.text = "Loading models from ${provider.displayName}..."
            refreshButton.isEnabled = false
            AppExecutorUtil.getAppExecutorService().execute {
                try {
                    val result = CliModelCatalog(provider).load(path, indicator)
                    ApplicationManager.getApplication().invokeLater({
                        if (modelLoad === indicator && !indicator.isCanceled) {
                            selector.updateModels(result.models)
                            status.text = if (result.fromCli) "Models loaded from ${provider.displayName}."
                            else "CLI model list unavailable. Using built-in suggestions; custom names are supported."
                            refreshButton.isEnabled = true
                        }
                    }, ModalityState.any())
                } catch (_: ProcessCanceledException) {
                    // Closing settings or changing the executable cancels this request.
                }
            }
        }

        val result = panel {
            row("${PluginFlavor.provider.displayName} executable:") {
                cell(executableField)
                    .bindText(settings::executablePath)
                    .comment("Leave empty to auto-detect. Example: /opt/homebrew/bin/${PluginFlavor.provider.executable}")
                    .align(AlignX.FILL)
            }
            row("Model:") {
                cell(selector)
                    .align(AlignX.FILL)
                    .onApply { settings.model = selector.selectedModel }
                    .onReset { selector.selectedModel = settings.model }
                    .onIsModified { selector.selectedModel != settings.model }
                    .comment("Choose a model or type its name. Default (or an empty field) uses your CLI configuration.")
                refreshButton = button("Refresh Models") { refreshModels() }.component
            }
            row {
                cell(status)
            }
            row("Effort level:") {
                comboBox(PluginFlavor.provider.effortLevels)
                    .bindItem(
                        { settings.effortLevel },
                        { settings.effortLevel = it ?: CommitSettings.EffortLevel.LOW }
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
                    settings.promptTemplate = CommitSettings.Companion.DEFAULT_PROMPT
                    // recreate panel to refresh the text area
                    reset()
                }
            }
        }
        executableField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                modelLoad?.cancel()
                modelLoad = null
                selector.updateModels(provider.fallbackModels)
                status.text = "Executable changed. Refresh models to load its available models."
                refreshButton.isEnabled = true
            }
        })
        refreshModels()
        return result
    }

    override fun disposeUIResources() {
        modelLoad?.cancel()
        modelLoad = null
        super.disposeUIResources()
    }
}
