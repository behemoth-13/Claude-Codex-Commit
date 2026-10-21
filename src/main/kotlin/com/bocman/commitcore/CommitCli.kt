package com.bocman.commitcore

import com.intellij.openapi.progress.ProgressIndicator
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Shared process lifecycle; provider-specific arguments and results live in [CliProvider]. */
internal class CommitCli(private val provider: CliProvider) {
    fun find(configuredPath: String): String? {
        val configured = configuredPath.trim()
        if (configured.isNotBlank()) {
            return if (File(configured).isFile && File(configured).canExecute()) configured
            else throw RuntimeException("Configured ${provider.displayName} path not executable: $configured")
        }
        val home = System.getProperty("user.home")
        val directories = listOf(
            "/usr/local/bin", "/opt/homebrew/bin", "$home/.local/bin", "$home/.npm-global/bin", "/usr/bin"
        ) + System.getenv("PATH").orEmpty().split(File.pathSeparator)
        return directories.filter { it.isNotBlank() }
            .map { File(it, provider.executable) }
            .firstOrNull { it.isFile && it.canExecute() }?.absolutePath
    }

    fun generate(
        executablePath: String,
        prompt: String,
        effort: String,
        indicator: ProgressIndicator,
        model: String = ""
    ): String {
        indicator.checkCanceled()
        val directory = Files.createTempDirectory("commit_core_").toFile()
        var process: Process? = null
        try {
            val input = directory.resolve("prompt.txt").apply { writeText(prompt) }
            val result = directory.resolve("result.txt")
            val builder = ProcessBuilder(provider.command(executablePath, effort, result, model.trim()))
                .redirectErrorStream(false)
                .redirectInput(input)
                .directory(provider.workingDirectory(directory))
            with(builder.environment()) {
                val home = System.getProperty("user.home")
                putIfAbsent("HOME", home)
                val path = getOrDefault("PATH", "/usr/local/bin:/usr/bin:/bin")
                put("PATH", "$path:/usr/local/bin:/opt/homebrew/bin:$home/.local/bin:$home/.npm-global/bin")
            }
            val running = builder.start()
            process = running
            val stdout = CompletableFuture.supplyAsync { running.inputStream.bufferedReader().use { it.readText() } }
            val stderr = CompletableFuture.supplyAsync { running.errorStream.bufferedReader().use { it.readText() } }
            while (!running.waitFor(100, TimeUnit.MILLISECONDS) || !stdout.isDone || !stderr.isDone) {
                indicator.checkCanceled()
                if (!running.isAlive) Thread.sleep(20)
            }
            indicator.checkCanceled()
            return provider.parse(stdout.get(), stderr.get(), running.exitValue(), result)
        } finally {
            try {
                process?.let { running ->
                    if (running.isAlive) {
                        running.descendants().use { children -> children.forEach { it.destroyForcibly() } }
                        running.destroyForcibly()
                        running.waitFor(5, TimeUnit.SECONDS)
                    }
                    runCatching { running.inputStream.close() }
                    runCatching { running.errorStream.close() }
                    runCatching { running.outputStream.close() }
                }
            } finally {
                directory.deleteRecursively()
            }
        }
    }
}
