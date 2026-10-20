package com.bocman.commitcore

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.testFramework.LightPlatformTestCase
import org.junit.Assert.assertThrows as expectThrows
import java.nio.file.Files

class ClaudeCliTest : LightPlatformTestCase() {
    fun testSuccessfulResultIsExtractedFromJson() {
        withCli(success("Fix generation\\n\\nPreserve selected changes")) { cli, input ->
            assertEquals("Fix generation\n\nPreserve selected changes", call(cli))
            val arguments = input.parentFile.resolve("arguments").readLines()
            assertTrue(arguments.toString(), arguments.windowed(2).contains(listOf("--output-format", "json")))
        }
    }

    fun testLargeUnicodePromptIsPassedIntactViaStdin() {
        val prompt = "Selected change: Привет 🌍\n".repeat(80_000)
        withCli(success("Summarize changes")) { cli, input ->
            assertEquals("Summarize changes", call(cli, prompt))
            assertEquals(prompt, input.readText())
        }
    }

    fun testJsonErrorWithZeroExitCodeIsNotACommitMessage() {
        withCli("""{"type":"result","subtype":"success","is_error":true,"result":"Prompt is too long"}""") { cli, _ ->
            val message = failure(cli)
            assertTrue(message, message.contains("Prompt is too long"))
            assertTrue(message, message.contains("Select fewer files or hunks"))
        }
    }

    fun testNonzeroExitPreservesStdoutAndStderr() {
        withCli("Request too large", "Additional CLI diagnostic", 1) { cli, _ ->
            val message = failure(cli)
            assertTrue(message, message.contains("Request too large"))
            assertTrue(message, message.contains("Additional CLI diagnostic"))
            assertTrue(message, message.contains("Select fewer files or hunks"))
        }
    }

    fun testStructuredErrorsAreReportedWithoutResult() {
        withCli("""{"type":"result","subtype":"error_during_execution","is_error":true,"errors":["Context window exceeded","Request rejected"]}""", exitCode = 1) { cli, _ ->
            val message = failure(cli)
            assertTrue(message, message.contains("Context window exceeded"))
            assertTrue(message, message.contains("Request rejected"))
            assertTrue(message, message.contains("Select fewer files or hunks"))
        }
    }

    fun testErrorSubtypeIsRejectedEvenWithFalseErrorFlag() {
        withCli("""{"type":"result","subtype":"error_max_turns","is_error":false,"result":"Incomplete summary"}""") { cli, _ ->
            val message = failure(cli)
            assertTrue(message, message.contains("error_max_turns"))
        }
    }

    fun testStderrOnlyFailure() {
        withCli("", "Unknown option: --effort", 1) { cli, _ ->
            assertTrue(failure(cli).contains("Unknown option: --effort"))
        }
    }

    fun testFailureWithoutOutputIncludesExitCode() {
        withCli("", exitCode = 137) { cli, _ ->
            assertTrue(failure(cli).contains("137"))
        }
    }

    fun testEmptyResultCannotReplaceCommitMessage() {
        withCli(success("   ")) { cli, _ ->
            assertTrue(failure(cli).contains("empty"))
        }
    }

    fun testMalformedOutputCannotReplaceCommitMessage() {
        for (output in listOf("", "Prompt is too long", "{broken", "[]", "{}",
            """{"type":"result","subtype":"success","result":"Missing status"}""",
            """{"type":"result","subtype":"success","is_error":false,"result":42}""")) {
            withCli(output) { cli, _ -> failure(cli) }
        }
    }

    fun testSuccessfulMessageMentioningLimitsIsNotRejected() {
        withCli(success("Handle prompt is too long errors"), "Harmless CLI warning") { cli, _ ->
            assertEquals("Handle prompt is too long errors", call(cli))
        }
    }

    fun testNonzeroExitOverridesSuccessPayload() {
        withCli(success("Partial result"), "CLI failed after generating output", 1) { cli, _ ->
            assertTrue(failure(cli).contains("CLI failed after generating output"))
        }
    }

    fun testLongDiagnosticsAreBoundedButStillClassified() {
        withCli("x".repeat(20_000) + "\nPrompt is too long", exitCode = 1) { cli, _ ->
            val message = failure(cli)
            assertTrue(message.length.toString(), message.length < 5_000)
            assertTrue(message, message.contains("Select fewer files or hunks"))
        }
    }

    fun testCancellationIsNotConvertedToCliFailure() {
        withCli(success("Unused")) { cli, _ ->
            val indicator = EmptyProgressIndicator().apply { cancel() }
            expectThrows(ProcessCanceledException::class.java) {
                CommitCli(CliProvider.CLAUDE).generate(cli, "prompt", "low", indicator)
            }
        }
    }

    private fun success(result: String) =
        """{"type":"result","subtype":"success","is_error":false,"result":"$result"}"""

    private fun call(cli: String, prompt: String = "Selected diff") =
        CommitCli(CliProvider.CLAUDE).generate(cli, prompt, "low", EmptyProgressIndicator())

    private fun failure(cli: String): String =
        expectThrows(RuntimeException::class.java) { call(cli) }.message.orEmpty()

    private fun withCli(
        stdout: String,
        stderr: String = "",
        exitCode: Int = 0,
        check: (String, java.io.File) -> Unit
    ) {
        val directory = Files.createTempDirectory("claude-cli-test-").toFile()
        try {
            val output = directory.resolve("stdout").apply { writeText(stdout) }
            val error = directory.resolve("stderr").apply { writeText(stderr) }
            val input = directory.resolve("stdin")
            val cli = directory.resolve("claude").apply {
                writeText("""
                    |#!/bin/sh
                    |printf '%s\n' "${'$'}@" > '${directory.resolve("arguments").path}'
                    |cat > '${input.path}'
                    |cat '${output.path}'
                    |cat '${error.path}' >&2
                    |exit $exitCode
                """.trimMargin() + "\n")
                check(setExecutable(true))
            }
            check(cli.path, input)
        } finally {
            directory.deleteRecursively()
        }
    }
}
