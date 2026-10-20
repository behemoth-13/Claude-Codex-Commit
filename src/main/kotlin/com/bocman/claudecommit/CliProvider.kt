package com.bocman.claudecommit

import java.io.File

internal enum class CliProvider(val displayName: String, val executable: String, val cliName: String) {
    CLAUDE("Claude", "claude", "Claude Code"),
    CODEX("Codex", "codex", "Codex");

    val pluginName: String get() = "$displayName Commit"
    val actionText: String get() = "Generate with $displayName"
    val effortLevels: List<ClaudeCommitSettings.EffortLevel>
        get() = ClaudeCommitSettings.EffortLevel.entries.filter {
            this != CODEX || it != ClaudeCommitSettings.EffortLevel.MAX
        }

    fun command(executablePath: String, effort: String, resultFile: File): List<String> = when (this) {
        CLAUDE -> listOf(executablePath, "--print", "--output-format", "json", "--effort", effort)
        CODEX -> listOf(
            executablePath, "--ask-for-approval", "never", "exec",
            "--sandbox", "read-only", "--ephemeral", "--skip-git-repo-check",
            "--color", "never", "-c", "model_reasoning_effort=\"$effort\"",
            "--output-last-message", resultFile.absolutePath, "-"
        )
    }

    fun parse(stdout: String, stderr: String, exitCode: Int, resultFile: File): String = when (this) {
        CLAUDE -> ClaudeCliResult.parse(stdout, stderr, exitCode)
        CODEX -> {
            if (exitCode != 0) {
                throw failure("Codex failed (exit code $exitCode).", stdout, stderr)
            }
            val result = resultFile.takeIf { it.isFile }?.readText()?.trim()
            if (result.isNullOrBlank()) {
                throw failure("Codex returned an empty commit message. Please try again.", stdout, stderr)
            }
            result
        }
    }

    private fun failure(summary: String, stdout: String, stderr: String): RuntimeException {
        val details = listOf(stdout, stderr).filter { it.isNotBlank() }.joinToString("\n\n") { it.take(1500) }
        return RuntimeException(summary + if (details.isEmpty()) "" else "\n\nCodex details:\n$details")
    }
}
