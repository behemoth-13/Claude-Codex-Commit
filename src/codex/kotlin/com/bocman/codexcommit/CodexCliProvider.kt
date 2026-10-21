package com.bocman.codexcommit

import com.bocman.commitcore.CliProvider
import com.bocman.commitcore.CommitSettings
import com.bocman.commitcore.ModelCatalogSession
import com.google.gson.JsonObject
import java.io.File

internal object CodexCliProvider : CliProvider {
    override val displayName = "Codex"
    override val executable = "codex"
    override val cliName = "Codex"
    override val effortLevels = CommitSettings.EffortLevel.entries.filter { it != CommitSettings.EffortLevel.MAX }
    override val fallbackModels = listOf("gpt-6.1-sol", "gpt-6-sol", "gpt-6-astra")

    override fun modelListCommand(executablePath: String) = listOf(executablePath, "app-server")

    override fun readModels(session: ModelCatalogSession): List<String> {
        session.send("""{"id":0,"method":"initialize","params":{"clientInfo":{"name":"codex_commit","version":"1.0"}}}""")
        val initialized = session.receive { it.get("id")?.asString == "0" }
        check(initialized.has("result") && !initialized.has("error")) { "CLI initialization failed" }
        session.send("""{"method":"initialized","params":{}}""")
        val models = mutableListOf<String>()
        val cursors = mutableSetOf<String>()
        var cursor: String? = null
        var id = 0
        do {
            val params = JsonObject().apply {
                addProperty("limit", 100)
                addProperty("includeHidden", false)
                cursor?.let { addProperty("cursor", it) }
            }
            val request = JsonObject().apply {
                addProperty("id", ++id)
                addProperty("method", "model/list")
                add("params", params)
            }
            session.send(request.toString())
            val response = session.receive { it.get("id")?.asString == id.toString() }
            check(!response.has("error")) { "Model catalog request failed" }
            val result = response.getAsJsonObject("result")
            models += result.getAsJsonArray("data").map { it.asJsonObject.get("model").asString }
            cursor = result.get("nextCursor")?.takeUnless { it.isJsonNull }?.asString
            check(cursor == null || cursors.add(cursor)) { "Model catalog repeated a page" }
        } while (cursor != null)
        return models
    }

    override fun command(executablePath: String, effort: String, resultFile: File, model: String): List<String> =
        listOf(
            executablePath, "--ask-for-approval", "never", "exec",
            "--sandbox", "read-only", "--ephemeral", "--skip-git-repo-check",
            "--color", "never", "-c", "model_reasoning_effort=\"$effort\"",
            "--output-last-message", resultFile.absolutePath
        ) + (if (model.isBlank()) emptyList() else listOf("--model", model)) + "-"

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
