package com.bocman.commitcore

import java.io.File

/** Provider-specific CLI protocol supplied by the active plugin flavor. */
internal interface CliProvider {
    val displayName: String
    val executable: String
    val cliName: String

    val pluginName: String get() = "$displayName Commit"
    val actionText: String get() = "Generate with $displayName"
    val effortLevels: List<CommitSettings.EffortLevel>

    fun command(executablePath: String, effort: String, resultFile: File): List<String>

    fun parse(stdout: String, stderr: String, exitCode: Int, resultFile: File): String

    /** Null inherits the IDE process's working directory. */
    fun workingDirectory(temporaryDirectory: File): File? = null
}
