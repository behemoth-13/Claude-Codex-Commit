package com.bocman.commitcore

import com.intellij.openapi.components.State
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.junit.Assert.assertThrows as expectThrows
import java.nio.file.Files

class FlavorIntegrationTest : LightPlatformTestCase() {
    fun testSettingsServiceKeepsItsIdentityAndLoadsExistingXml() {
        val settings = PluginFlavor.settings()
        val original = settings.state
        val codex = PluginFlavor.provider.executable == "codex"
        val providerName = if (codex) "Codex" else "Claude"
        val providerPackage = if (codex) "codexcommit" else "claudecommit"
        assertEquals("com.bocman.$providerPackage.${providerName}CommitSettings", settings.javaClass.name)
        val state = settings.javaClass.getAnnotation(State::class.java)
        assertEquals("${providerName}CommitSettings", state.name)
        assertEquals(if (codex) "codex-commit.xml" else "claude-commit.xml", state.storages.single().value)

        val saved = Element("State")
        for ((name, value) in mapOf(
            "claudePath" to "/existing/claude",
            "codexPath" to "/existing/codex",
            "promptTemplate" to "Branch={BRANCH}\nDiff={DIFF}",
            "effortLevel" to "high"
        )) {
            saved.addContent(Element("option").setAttribute("name", name).setAttribute("value", value))
        }
        try {
            settings.loadState(XmlSerializer.deserialize(saved, original.javaClass))
            assertEquals(if (codex) "/existing/codex" else "/existing/claude", settings.executablePath)
            val serialized = XmlSerializer.serialize(settings.state)
            val pathKey = if (codex) "codexPath" else "claudePath"
            val pathOption = serialized.getChildren("option").single { it.getAttributeValue("name") == pathKey }
            assertEquals(if (codex) "/existing/codex" else "/existing/claude", pathOption.getAttributeValue("value"))
            assertEquals("Branch={BRANCH}\nDiff={DIFF}", settings.promptTemplate)
            assertEquals(CommitSettings.EffortLevel.HIGH, settings.effortLevel)
            assertEquals("", settings.model)
        } finally {
            settings.loadState(original)
        }
    }

    fun testActiveFlavorGeneratesUsingItsPersistedSettings() {
        val settings = PluginFlavor.settings()
        val original = settings.state
        val directory = Files.createTempDirectory("commit-flavor-test-").toFile()
        try {
            val cli = directory.resolve("configured cli").apply {
                writeText(
                    """
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
                """.trimMargin() + "\n"
                )
                check(setExecutable(true))
            }
            settings.loadState(XmlSerializer.deserialize(Element("State"), original.javaClass))
            settings.executablePath = cli.path
            settings.promptTemplate = "Branch={BRANCH}\nDiff={DIFF}"
            settings.effortLevel = CommitSettings.EffortLevel.HIGH
            // Round-trip the same XML format used by persisted application settings.
            val saved = XmlSerializer.serialize(settings.state)
            saved.addContent(Element("option").setAttribute("name", "model").setAttribute("value", "future-model"))
            settings.loadState(XmlSerializer.deserialize(saved, original.javaClass))
            val result = CommitMessageGenerator.generate("TEST-123", "+selected hunk", EmptyProgressIndicator())
            assertEquals("Branch=TEST-123\nDiff=+selected hunk", directory.resolve("prompt").readText())
            val arguments = directory.resolve("arguments").readLines()
            assertTrue(arguments.toString(), arguments.windowed(2).contains(listOf("--model", "future-model")))
            assertEquals("future-model", settings.model)
            settings.model = "  "
            CommitMessageGenerator.generate("TEST-123", "+selected hunk", EmptyProgressIndicator())
            assertFalse(directory.resolve("arguments").readLines().contains("--model"))
            if (PluginFlavor.provider.executable == "codex") {
                assertEquals("Generated with Codex", result)
                assertTrue(arguments.contains("model_reasoning_effort=\"high\""))
            } else {
                assertEquals("Generated with Claude", result)
                assertTrue(arguments.windowed(2).contains(listOf("--effort", "high")))
            }
            val state = settings.javaClass.getAnnotation(State::class.java)
            val expectedStorage =
                if (PluginFlavor.provider.executable == "codex") "codex-commit.xml" else "claude-commit.xml"
            assertEquals(expectedStorage, state.storages.single().value)
        } finally {
            settings.loadState(original)
            directory.deleteRecursively()
        }
    }

    fun testEffortLevelsKeepFlavorSpecificLimitsAfterReload() {
        val settings = PluginFlavor.settings()
        val original = settings.state
        try {
            val saved = Element("State").addContent(
                Element("option").setAttribute("name", "effortLevel").setAttribute("value", "max")
            )
            settings.loadState(XmlSerializer.deserialize(saved, original.javaClass))
            val expected = if (PluginFlavor.provider.executable == "codex") {
                CommitSettings.EffortLevel.LOW
            } else {
                CommitSettings.EffortLevel.MAX
            }
            assertEquals(expected, settings.effortLevel)
            settings.effortLevel = CommitSettings.EffortLevel.XHIGH
            assertEquals(CommitSettings.EffortLevel.XHIGH, settings.effortLevel)
        } finally {
            settings.loadState(original)
        }
    }

    fun testInvalidExplicitCliPathNeverFallsBackToInstalledCli() {
        val directory = Files.createTempDirectory("invalid-commit-cli-").toFile()
        try {
            val cli = CommitCli(PluginFlavor.provider)
            expectThrows(RuntimeException::class.java) { cli.find(directory.resolve("missing").path) }
            expectThrows(RuntimeException::class.java) { cli.find(directory.path) }
            val nonExecutable = directory.resolve("plain-file").apply { writeText("not executable") }
            expectThrows(RuntimeException::class.java) { cli.find(nonExecutable.path) }
        } finally {
            directory.deleteRecursively()
        }
    }
}
