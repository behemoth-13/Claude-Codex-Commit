package com.bocman.commitcore

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.testFramework.LightPlatformTestCase
import org.junit.Assert.assertThrows as expectThrows
import java.io.File
import java.nio.file.Files

class CliModelCatalogTest : LightPlatformTestCase() {
    private val codex get() = PluginFlavor.provider.executable == "codex"

    fun testLoadsModelsWithoutStartingGenerationAndClosesProcess() {
        withCli(successScript()) { cli, directory ->
            val result = CliModelCatalog(PluginFlavor.provider).load(cli, EmptyProgressIndicator())
            assertTrue(result.fromCli)
            assertEquals(listOf("new-model", "another-model"), result.models)
            val requests = directory.resolve("requests").readText()
            assertTrue(requests, requests.contains("initialize"))
            assertFalse(requests, requests.contains("thread/start"))
            assertFalse(requests, requests.contains("\"type\":\"user\""))
            assertStopped(directory)
        }
    }

    fun testUnsupportedOrMalformedCatalogUsesFallback() {
        for (reply in listOf(
            "not json", "{}", if (codex) {
                """{"id":0,"error":{"code":-32601,"message":"Unsupported"}}"""
            } else {
                """{"type":"control_response","response":{"request_id":"models","subtype":"error","error":"Unsupported"}}"""
            }
        )) {
            withCli("read request\necho '$reply'") { cli, _ ->
                val result = CliModelCatalog(PluginFlavor.provider).load(cli, EmptyProgressIndicator())
                assertFalse(result.fromCli)
                assertFalse(result.models.isEmpty())
            }
        }
    }

    fun testMissingExecutableUsesFallback() {
        val result = CliModelCatalog(PluginFlavor.provider).load("/missing/commit-cli", EmptyProgressIndicator())
        assertFalse(result.fromCli)
        assertFalse(result.models.isEmpty())
    }

    fun testTimeoutStopsUnresponsiveProcess() {
        withCli("exec sleep 30") { cli, directory ->
            val started = System.nanoTime()
            val result =
                CliModelCatalog(PluginFlavor.provider, timeoutMillis = 1_000).load(cli, EmptyProgressIndicator())
            assertFalse(result.fromCli)
            assertTrue("Catalog timeout was not bounded", System.nanoTime() - started < 3_000_000_000)
            assertStopped(directory)
        }
    }

    fun testCancellationStopsProcessAndIsNotFallback() {
        withCli("exec sleep 30") { cli, directory ->
            val indicator = EmptyProgressIndicator()
            val cancellation = kotlin.concurrent.thread(isDaemon = true) {
                val deadline = System.nanoTime() + 3_000_000_000
                while (!directory.resolve("pid").exists() && System.nanoTime() < deadline) Thread.sleep(10)
                indicator.cancel()
            }
            try {
                expectThrows(ProcessCanceledException::class.java) {
                    CliModelCatalog(PluginFlavor.provider).load(cli, indicator)
                }
                assertStopped(directory)
            } finally {
                cancellation.join(4_000)
            }
        }
    }

    private fun successScript(): String = if (codex) {
        """
        read request; echo "${'$'}request" >> requests
        echo '{"id":0,"result":{"userAgent":"test"}}'
        read request; echo "${'$'}request" >> requests
        read request; echo "${'$'}request" >> requests
        echo '{"method":"notification"}'
        echo '{"id":1,"result":{"data":[{"model":"new-model"}],"nextCursor":"page-2"}}'
        read request; echo "${'$'}request" >> requests
        echo '{"id":2,"result":{"data":[{"model":"new-model"},{"model":"another-model"}],"nextCursor":null}}'
        exec sleep 30
        """.trimIndent()
    } else {
        """
        read request; echo "${'$'}request" >> requests
        echo '{"type":"control_response","response":{"request_id":"models","subtype":"success","response":{"models":[{"value":"new-model","displayName":"New Model"},{"value":"another-model","displayName":"Another Model"}]}}}'
        exec sleep 30
        """.trimIndent()
    }

    private fun assertStopped(directory: File) {
        val pid = directory.resolve("pid").readText().trim().toLong()
        assertFalse(ProcessHandle.of(pid).map { it.isAlive }.orElse(false))
    }

    private fun withCli(script: String, check: (String, File) -> Unit) {
        val directory = Files.createTempDirectory("model-catalog-test-").toFile()
        try {
            val cli = directory.resolve("cli with spaces").apply {
                writeText("#!/bin/sh\ncd '${directory.path}'\necho ${'$'}${'$'} > pid\n$script\n")
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
