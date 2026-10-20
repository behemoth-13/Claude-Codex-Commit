package com.bocman.claudecommit

import com.intellij.openapi.components.State
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.xmlb.XmlSerializer
import org.junit.Assert.assertThrows as expectThrows
import java.nio.file.Files

class FlavorIntegrationTest : LightPlatformTestCase() {
    fun testActiveFlavorGeneratesUsingItsPersistedSettings() {
        val settings = ClaudeCommitSettings.getInstance()
        val original = settings.state
        val directory = Files.createTempDirectory("commit-flavor-test-").toFile()
        try {
            val cli = directory.resolve("configured cli").apply {
                writeText("""
                    |#!/bin/sh
                    |printf '%s\n' "${'$'}@" > '${directory.resolve("arguments")}'
                    |cat > '${directory.resolve("prompt")}'
                    |if [ "${'$'}1" = "--print" ]; then
                    |  echo '{"type":"result","subtype":"success","is_error":false,"result":"Generated with Claude"}'
                    |else
                    |  while [ "${'$'}#" -gt 0 ]; do
                    |    if [ "${'$'}1" = "--output-last-message" ]; then
                    |      shift
                    |      printf '%s' 'Generated with Codex' > "${'$'}1"
                    |    fi
                    |    shift
                    |  done
                    |fi
                """.trimMargin() + "\n")
                check(setExecutable(true))
            }
            settings.loadState(ClaudeCommitSettings.State())
            settings.executablePath = cli.path
            settings.promptTemplate = "Branch={BRANCH}\nDiff={DIFF}"
            settings.effortLevel = ClaudeCommitSettings.EffortLevel.HIGH
            // Round-trip the same XML format used by persisted application settings.
            val saved = XmlSerializer.serialize(settings.state)
            settings.loadState(XmlSerializer.deserialize(saved, ClaudeCommitSettings.State::class.java))
            val result = CommitMessageGenerator.generate("TEST-123", "+selected hunk", EmptyProgressIndicator())
            assertEquals("Branch=TEST-123\nDiff=+selected hunk", directory.resolve("prompt").readText())
            val arguments = directory.resolve("arguments").readLines()
            if (PluginFlavor.provider == CliProvider.CODEX) {
                assertEquals("Generated with Codex", result)
                assertTrue(arguments.contains("model_reasoning_effort=\"high\""))
                assertEquals("", settings.claudePath)
            } else {
                assertEquals("Generated with Claude", result)
                assertTrue(arguments.windowed(2).contains(listOf("--effort", "high")))
                assertEquals(cli.path, settings.claudePath)
            }
            val state = settings.javaClass.getAnnotation(State::class.java)
            val expectedStorage = if (PluginFlavor.provider == CliProvider.CODEX) "codex-commit.xml" else "claude-commit.xml"
            assertEquals(expectedStorage, state.storages.single().value)
        } finally {
            settings.loadState(original)
            directory.deleteRecursively()
        }
    }

    fun testInvalidExplicitCliPathNeverFallsBackToInstalledCli() {
        val directory = Files.createTempDirectory("invalid-commit-cli-").toFile()
        try {
            for (provider in CliProvider.entries) {
                val cli = CommitCli(provider)
                expectThrows(RuntimeException::class.java) { cli.find(directory.resolve("missing").path) }
                expectThrows(RuntimeException::class.java) { cli.find(directory.path) }
                val nonExecutable = directory.resolve("plain-file").apply { writeText("not executable") }
                expectThrows(RuntimeException::class.java) { cli.find(nonExecutable.path) }
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}
