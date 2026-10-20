package com.bocman.commitcore

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.testFramework.LightPlatformTestCase
import org.junit.Assert.assertThrows as expectThrows
import java.io.File
import java.nio.file.Files

class CodexCliTest : LightPlatformTestCase() {
    fun testOnlyFinalMessageIsReturnedAndPromptIsPassedIntact() {
        val prompt = "Selected change: Привет 🌍\n".repeat(80_000)
        withCli { cli, directory ->
            assertEquals("Fix selected changes\n\nPreserve unchecked hunks", call(cli, prompt))
            assertEquals(prompt, directory.resolve("stdin").readText())
            val args = directory.resolve("arguments").readLines()
            assertTrue(args.toString(), args.contains("exec"))
            assertTrue(args.toString(), args.windowed(2).contains(listOf("--sandbox", "read-only")))
            assertTrue(args.toString(), args.contains("--ephemeral"))
            assertTrue(args.toString(), args.contains("--skip-git-repo-check"))
            assertTrue(args.toString(), args.contains("model_reasoning_effort=\"low\""))
            assertEquals("-", args.last())
            assertFalse(File(directory.resolve("output-path").readText()).exists())
            assertFalse(File(directory.resolve("working-directory").readText().trim()).exists())
        }
    }

    fun testNonzeroExitRejectsPartialResultAndIncludesDiagnostics() {
        withCli(exitCode = 1) { cli, _ ->
            val message = expectThrows(RuntimeException::class.java) { call(cli) }.message.orEmpty()
            assertTrue(message, message.contains("Codex failed"))
            assertTrue(message, message.contains("CLI diagnostic"))
            assertFalse(message, message.startsWith("Fix selected"))
        }
    }

    fun testEmptyFinalMessageCannotReplaceCommitMessage() {
        withCli(result = " \n") { cli, _ ->
            val message = expectThrows(RuntimeException::class.java) { call(cli) }.message.orEmpty()
            assertTrue(message, message.contains("empty"))
        }
    }

    fun testMissingFinalMessageCannotFallBackToProgressOutput() {
        withCli(writeResult = false) { cli, _ ->
            expectThrows(RuntimeException::class.java) { call(cli) }
        }
    }

    fun testCancellationAfterStdoutClosesStillStopsTheProcess() {
        withCli(waitAfterOutput = true) { cli, directory ->
            val indicator = EmptyProgressIndicator()
            val cancellation = kotlin.concurrent.thread(isDaemon = true) {
                val deadline = System.nanoTime() + 5_000_000_000
                while (!directory.resolve("ready").exists() && System.nanoTime() < deadline) {
                    Thread.sleep(10)
                }
                indicator.cancel()
            }
            try {
                expectThrows(ProcessCanceledException::class.java) {
                    call(cli, "Selected diff", indicator)
                }
            } finally {
                cancellation.join(6_000)
            }
            val pid = directory.resolve("pid").readText().trim().toLong()
            assertFalse(ProcessHandle.of(pid).map { it.isAlive }.orElse(false))
            assertFalse(File(directory.resolve("output-path").readText()).exists())
        }
    }

    private fun call(
        cli: String,
        prompt: String = "Selected diff",
        indicator: EmptyProgressIndicator = EmptyProgressIndicator()
    ): String = CommitCli(CliProvider.CODEX).generate(cli, prompt, "low", indicator)

    private fun withCli(
        result: String = "Fix selected changes\n\nPreserve unchecked hunks",
        exitCode: Int = 0,
        writeResult: Boolean = true,
        waitAfterOutput: Boolean = false,
        check: (String, File) -> Unit
    ) {
        val directory = Files.createTempDirectory("codex-cli-test-").toFile()
        try {
            directory.resolve("result").writeText(result)
            val cli = directory.resolve("codex with spaces").apply {
                writeText("""
                    |#!/bin/sh
                    |printf '%s\n' "${'$'}@" > '${directory.resolve("arguments")}'
                    |printf '%s\n' "${'$'}${'$'}" > '${directory.resolve("pid")}'
                    |pwd > '${directory.resolve("working-directory")}'
                    |cat > '${directory.resolve("stdin")}'
                    |while [ "${'$'}#" -gt 0 ]; do
                    |  if [ "${'$'}1" = "--output-last-message" ]; then
                    |    shift
                    |    printf '%s' "${'$'}1" > '${directory.resolve("output-path")}'
                    |    ${if (writeResult) "cat '${directory.resolve("result")}' > \"${'$'}1\"" else ":"}
                    |  fi
                    |  shift
                    |done
                    |echo 'Progress output is not a commit message'
                    |echo 'CLI diagnostic' >&2
                    |${if (waitAfterOutput) "exec 1>&- 2>&-\ntouch '${directory.resolve("ready")}'\nexec sleep 30" else ""}
                    |exit $exitCode
                """.trimMargin() + "\n")
                check(setExecutable(true))
            }
            check(cli.path, directory)
        } finally {
            directory.resolve("pid").takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull()?.let {
                ProcessHandle.of(it).ifPresent { process -> if (process.isAlive) process.destroyForcibly() }
            }
            directory.deleteRecursively()
        }
    }
}
