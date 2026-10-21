package com.bocman.commitcore

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.TimeUnit

internal data class ModelCatalogResult(val models: List<String>, val fromCli: Boolean)

/** A short metadata-only CLI session; it never sends a prompt or starts a turn. */
internal class CliModelCatalog(private val provider: CliProvider, private val timeoutMillis: Long = 10_000) {
    fun load(configuredPath: String, indicator: ProgressIndicator): ModelCatalogResult {
        indicator.checkCanceled()
        try {
            val executable = CommitCli(provider).find(configuredPath) ?: return fallback()
            val directory = Files.createTempDirectory("commit-models-").toFile()
            var process: Process? = null
            try {
                val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
                val running = cliProcessBuilder(provider.modelListCommand(executable))
                    .directory(directory)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
                process = running
                val models = provider.readModels(ModelCatalogSession(running, indicator, deadline))
                    .map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                indicator.checkCanceled()
                return if (models.isEmpty()) fallback() else ModelCatalogResult(models, true)
            } finally {
                try {
                    process?.let { running ->
                        try {
                            running.descendants().use { children -> children.forEach { it.destroyForcibly() } }
                            running.destroyForcibly()
                            running.waitFor(1, TimeUnit.SECONDS)
                        } finally {
                            runCatching { running.inputStream.close() }
                            runCatching { running.outputStream.close() }
                            runCatching { running.errorStream.close() }
                        }
                    }
                } finally {
                    directory.deleteRecursively()
                }
            }
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ProcessCanceledException(e)
        } catch (_: Exception) {
            indicator.checkCanceled()
            return fallback()
        }
    }

    private fun fallback() = ModelCatalogResult(provider.fallbackModels, false)
}

/** Poll raw bytes so incomplete lines cannot block cancellation or the overall deadline. */
internal class ModelCatalogSession(
    private val process: Process,
    private val indicator: ProgressIndicator,
    private val deadline: Long
) {
    private var bytesRead = 0
    private val line = ByteArrayOutputStream()

    fun send(json: String) {
        checkActive()
        process.outputStream.write((json + "\n").toByteArray(Charsets.UTF_8))
        process.outputStream.flush()
    }

    fun receive(matches: (JsonObject) -> Boolean): JsonObject {
        while (true) {
            checkActive()
            if (process.inputStream.available() == 0) {
                if (!process.isAlive) throw IOException("CLI closed the model catalog stream")
                Thread.sleep(20)
                continue
            }
            val byte = process.inputStream.read()
            if (byte < 0) throw IOException("CLI closed the model catalog stream")
            if (++bytesRead > 1_048_576) throw IOException("Model catalog response is too large")
            if (byte == '\n'.code) {
                val message = JsonParser.parseString(line.toString(Charsets.UTF_8)).asJsonObject
                line.reset()
                if (matches(message)) return message
            } else {
                line.write(byte)
            }
        }
    }

    private fun checkActive() {
        indicator.checkCanceled()
        if (System.nanoTime() >= deadline) throw IOException("Model catalog request timed out")
    }
}
