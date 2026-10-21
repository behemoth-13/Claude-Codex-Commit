package com.bocman.commitcore

import com.intellij.ide.DataManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.InclusionListener
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.event.AncestorEvent
import javax.swing.event.AncestorListener

/**
 * Adds a generation button to the commit-options strip (the row
 * containing the Amend checkbox). Both entry points use [CommitMessageGenerator].
 */
class CommitHandler(private val panel: CheckinProjectPanel) : CheckinHandler() {

    companion object {
        private val LOG = Logger.getInstance(CommitHandler::class.java)
    }

    private val worker = GenerateCommitMessageAction()

    override fun getBeforeCheckinConfigurationPanel(): RefreshableOnComponent {
        val button = JButton(PluginFlavor.provider.actionText).apply {
            isEnabled = false
            toolTipText = "Select files to commit first"
        }

        var lastEnabled = false
        fun updateButton() {
            val hasChanges = panel.selectedChanges.isNotEmpty()
            if (hasChanges != lastEnabled) {
                LOG.info("[CommitCore] Button ${if (hasChanges) "enabled" else "disabled"} — ${panel.selectedChanges.size} file(s) checked")
                lastEnabled = hasChanges
            }
            button.isEnabled = hasChanges
            button.toolTipText = if (hasChanges)
                "Generate a commit message using the local ${PluginFlavor.provider.cliName} CLI"
            else
                "Select files to commit first"
        }

        button.addAncestorListener(object : AncestorListener {
            private var listenerAttached = false

            override fun ancestorAdded(e: AncestorEvent) {
                updateButton()
                if (!listenerAttached) {
                    val ui = DataManager.getInstance()
                        .getDataContext(panel.component)
                        .getData(VcsDataKeys.COMMIT_WORKFLOW_UI)
                    if (ui != null) {
                        ui.addInclusionListener(
                            object : InclusionListener {
                                override fun inclusionChanged() =
                                    ApplicationManager.getApplication().invokeLater { updateButton() }
                            },
                            ui
                        )
                        listenerAttached = true
                        LOG.info("[CommitCore] InclusionListener attached via CommitWorkflowUi")
                    } else {
                        LOG.warn("[CommitCore] CommitWorkflowUi not available — button state won't update dynamically")
                    }
                }
            }

            override fun ancestorRemoved(e: AncestorEvent) {}
            override fun ancestorMoved(e: AncestorEvent) {}
        })

        button.addActionListener { generateCommitMessage() }

        return object : RefreshableOnComponent {
            override fun getComponent(): JComponent = button
            override fun saveState() {}
            override fun restoreState() {}
        }
    }

    private fun generateCommitMessage() {
        val project = panel.project
        val selectedChanges = panel.selectedChanges.toList()

        if (selectedChanges.isEmpty()) {
            LOG.info("[CommitCore] Handler button clicked with no checked files")
            Messages.showWarningDialog(
                project,
                "No changes selected. Check the files you want to commit first.",
                PluginFlavor.provider.pluginName
            )
            return
        }

        LOG.info("[CommitCore] Handler button clicked — ${selectedChanges.size} file(s): ${selectedChanges.map { it.virtualFile?.name ?: "?" }}")

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, "Generating commit message with ${PluginFlavor.provider.displayName}…", /* canBeCancelled = */ true
        ) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text = "Reading branch name…"
                    val branch = worker.getBranchName(project)
                    LOG.info("[CommitCore] Branch: $branch")

                    indicator.text = "Reading diff…"
                    val diff = worker.getDiff(project, selectedChanges)

                    if (diff.isBlank()) {
                        LOG.warn("[CommitCore] Diff is empty for selected files")
                        showWarning(project, "No diff found for the selected files.")
                        return
                    }
                    LOG.info("[CommitCore] Diff: ${diff.lines().size} lines, ${diff.length} chars")
                    LOG.debug("[CommitCore] Diff content:\n$diff")

                    indicator.checkCanceled()
                    indicator.text = "Calling ${PluginFlavor.provider.displayName}…"

                    val message = CommitMessageGenerator.generate(branch, diff, indicator)

                    ApplicationManager.getApplication().invokeLater {
                        panel.setCommitMessage(message.trim())
                    }
                } catch (ex: ProcessCanceledException) {
                    LOG.info("[CommitCore] Generation cancelled by user")
                    throw ex
                } catch (ex: Exception) {
                    LOG.warn("[CommitCore] Generation failed", ex)
                    showError(project, ex.message ?: ex.toString())
                }
            }
        })
    }

    private fun showWarning(project: Project, msg: String) =
        ApplicationManager.getApplication().invokeLater {
            Messages.showWarningDialog(project, msg, PluginFlavor.provider.pluginName)
        }

    private fun showError(project: Project, msg: String) =
        ApplicationManager.getApplication().invokeLater {
            Messages.showErrorDialog(project, msg, "${PluginFlavor.provider.pluginName} — Error")
        }
}
