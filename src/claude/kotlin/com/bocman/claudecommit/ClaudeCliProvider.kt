package com.bocman.claudecommit

import com.bocman.commitcore.CliProvider
import com.bocman.commitcore.CommitSettings
import java.io.File

internal object ClaudeCliProvider : CliProvider {
    override val displayName = "Claude"
    override val executable = "claude"
    override val cliName = "Claude Code"
    override val effortLevels = CommitSettings.EffortLevel.entries

    override fun command(executablePath: String, effort: String, resultFile: File): List<String> =
        listOf(executablePath, "--print", "--output-format", "json", "--effort", effort)

    override fun parse(stdout: String, stderr: String, exitCode: Int, resultFile: File): String =
        ClaudeCliResult.parse(stdout, stderr, exitCode)
}
