package com.bocman.claudecommit

import com.bocman.commitcore.CliProvider
import com.bocman.commitcore.CommitSettings
import com.bocman.commitcore.ModelCatalogSession
import java.io.File

internal object ClaudeCliProvider : CliProvider {
    override val displayName = "Claude"
    override val executable = "claude"
    override val cliName = "Claude Code"
    override val effortLevels = CommitSettings.EffortLevel.entries
    override val fallbackModels = listOf("opus", "sonnet", "haiku")

    override fun modelListCommand(executablePath: String) = listOf(
        executablePath, "--print", "--input-format", "stream-json", "--output-format", "stream-json",
        "--verbose", "--no-session-persistence", "--strict-mcp-config", "--mcp-config", "{\"mcpServers\":{}}",
        "--settings", "{\"disableAllHooks\":true}"
    )

    override fun readModels(session: ModelCatalogSession): List<String> {
        session.send("""{"type":"control_request","request_id":"models","request":{"subtype":"initialize"}}""")
        val response = session.receive {
            it.get("type")?.asString == "control_response" &&
                    it.getAsJsonObject("response")?.get("request_id")?.asString == "models"
        }.getAsJsonObject("response")
        check(response.get("subtype")?.asString == "success") { "Model catalog request failed" }
        return response.getAsJsonObject("response").getAsJsonArray("models")
            .map { it.asJsonObject.get("value").asString }
            .filter { it != "default" }
    }

    override fun command(executablePath: String, effort: String, resultFile: File, model: String): List<String> =
        listOf(executablePath, "--print", "--output-format", "json", "--effort", effort) +
                if (model.isBlank()) emptyList() else listOf("--model", model)

    override fun parse(stdout: String, stderr: String, exitCode: Int, resultFile: File): String =
        ClaudeCliResult.parse(stdout, stderr, exitCode)
}
