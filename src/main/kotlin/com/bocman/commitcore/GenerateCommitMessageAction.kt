package com.bocman.commitcore

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diff.impl.patch.BinaryFilePatch
import com.intellij.openapi.diff.impl.patch.IdeaTextPatchBuilder
import com.intellij.openapi.diff.impl.patch.UnifiedDiffWriter
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import git4idea.repo.GitRepositoryManager
import java.io.StringWriter
import java.nio.file.Path

class GenerateCommitMessageAction : AnAction(PluginFlavor.provider.actionText), DumbAware {

    companion object {
        private val LOG = Logger.getInstance(GenerateCommitMessageAction::class.java)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val ui = e.getData(VcsDataKeys.COMMIT_WORKFLOW_UI)
        e.presentation.isVisible = e.project != null
        e.presentation.isEnabled = ui != null && ui.getIncludedChanges().isNotEmpty()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val commitMessageI = e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL)
        val changes = e.getData(VcsDataKeys.COMMIT_WORKFLOW_UI)?.getIncludedChanges() ?: emptyList()

        if (changes.isEmpty()) {
            LOG.info("[CommitCore] Toolbar action triggered with no checked files")
            Messages.showWarningDialog(
                project,
                "No files checked. Select files to commit first.",
                PluginFlavor.provider.pluginName
            )
            return
        }

        LOG.info("[CommitCore] Toolbar action triggered — ${changes.size} file(s): ${changes.map { it.virtualFile?.name ?: "?" }}")

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, "Generating commit message with ${PluginFlavor.provider.displayName}…", /* canBeCancelled = */ true
        ) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text = "Reading branch name…"
                    val branch = getBranchName(project)
                    LOG.info("[CommitCore] Branch: $branch")

                    indicator.text = "Reading diff…"
                    val diff = getDiff(project, changes)

                    if (diff.isBlank()) {
                        LOG.warn("[CommitCore] Diff is empty for selected files")
                        showWarning(
                            project,
                            "No diff found for the selected files.\nMake sure the files have unsaved changes."
                        )
                        return
                    }
                    LOG.info("[CommitCore] Diff: ${diff.lines().size} lines, ${diff.length} chars")
                    LOG.debug("[CommitCore] Diff content:\n$diff")

                    indicator.checkCanceled()
                    indicator.text = "Calling ${PluginFlavor.provider.displayName}…"

                    val message = CommitMessageGenerator.generate(branch, diff, indicator)

                    ApplicationManager.getApplication().invokeLater {
                        commitMessageI?.setCommitMessage(message.trim())
                            ?: showInfo(project, message.trim())
                    }
                } catch (_: ProcessCanceledException) {
                    LOG.info("[CommitCore] Generation cancelled by user")
                } catch (ex: Exception) {
                    LOG.warn("[CommitCore] Generation failed", ex)
                    showError(project, ex.message ?: ex.toString())
                }
            }
        })
    }

    // -------------------------------------------------------------------------
    // Git helpers
    // -------------------------------------------------------------------------

    internal fun getBranchName(project: Project): String =
        GitRepositoryManager.getInstance(project)
            .repositories
            .firstOrNull()
            ?.currentBranchName
            ?: "unknown"

    internal fun getDiff(project: Project, changes: Collection<Change>): String {
        if (changes.isEmpty()) return ""
        val basePath = Path.of(project.basePath ?: error("Project base directory is not available"))

        // Keep ChangeListChange objects intact: IntelliJ uses their changelist IDs
        // and line-status trackers to omit unchecked hunks and individual lines.
        val patches = IdeaTextPatchBuilder.buildPatch(project, changes, basePath, false, true)
        return StringWriter().also { writer ->
            UnifiedDiffWriter.write(project, basePath, patches, writer, "\n", null, emptyList())
            // UnifiedDiffWriter skips binary patches. Preserve their presence in the prompt.
            for (patch in patches.filterIsInstance<BinaryFilePatch>()) {
                val before = if (patch.isNewFile) "/dev/null" else patch.beforeName
                val after = if (patch.isDeletedFile) "/dev/null" else patch.afterName
                writer.append("Binary files $before and $after differ\n")
            }
        }.toString()
    }

    // -------------------------------------------------------------------------
    // UI helpers
    // -------------------------------------------------------------------------

    private fun showWarning(project: Project, msg: String) =
        ApplicationManager.getApplication().invokeLater {
            Messages.showWarningDialog(project, msg, PluginFlavor.provider.pluginName)
        }

    private fun showError(project: Project, msg: String) =
        ApplicationManager.getApplication().invokeLater {
            Messages.showErrorDialog(project, msg, "${PluginFlavor.provider.pluginName} — Error")
        }

    private fun showInfo(project: Project, msg: String) =
        ApplicationManager.getApplication().invokeLater {
            Messages.showInfoMessage(project, msg, PluginFlavor.provider.pluginName)
        }
}
