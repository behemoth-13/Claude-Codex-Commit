package com.bocman.codexcommit

import com.bocman.commitcore.CliProvider
import com.bocman.commitcore.CommitSettings
import java.io.File

internal object CodexCliProvider : CliProvider {
    override val displayName = "Codex"
    override val executable = "codex"
    override val cliName = "Codex"
    override val effortLevels = CommitSettings.EffortLevel.entries.filter { it != CommitSettings.EffortLevel.MAX }

    override fun command(executablePath: String, effort: String, resultFile: File): List<String> = listOf(
        executablePath, "--ask-for-approval", "never", "exec",
        "--sandbox", "read-only", "--ephemeral", "--skip-git-repo-check",
        "--color", "never", "-c", "model_reasoning_effort=\"$effort\"",
        "--output-last-message", resultFile.absolutePath, "-"
    )

    override fun parse(stdout: String, stderr: String, exitCode: Int, resultFile: File): String {
        if (exitCode != 0) {
            throw failure("Codex failed (exit code $exitCode).", stdout, stderr)
        }
        val result = resultFile.takeIf { it.isFile }?.readText()?.trim()
        if (result.isNullOrBlank()) {
            throw failure("Codex returned an empty commit message. Please try again.", stdout, stderr)
        }
        return result
    }

    // Only the selected patch is provided, without project instructions or repository discovery.
    override fun workingDirectory(temporaryDirectory: File): File = temporaryDirectory

    private fun failure(summary: String, stdout: String, stderr: String): RuntimeException {
        val details = listOf(stdout, stderr).filter { it.isNotBlank() }.joinToString("\n\n") { it.take(1500) }
        return RuntimeException(summary + if (details.isEmpty()) "" else "\n\nCodex details:\n$details")
    }
}
