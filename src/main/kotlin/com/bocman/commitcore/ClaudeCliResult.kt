package com.bocman.commitcore

import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonParser

/** Validates the CLI result before it can replace the user's commit message. */
internal object ClaudeCliResult {
    fun parse(stdout: String, stderr: String, exitCode: Int): String {
        val response = try {
            JsonParser.parseString(stdout).takeIf { it.isJsonObject }?.asJsonObject
        } catch (_: JsonParseException) {
            null
        }
        val type = response?.get("type").stringValue()
        val subtype = response?.get("subtype").stringValue()
        val isError = response?.get("is_error")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean
        val result = response?.get("result").stringValue()
        val errors = response?.get("errors")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { it.stringValue() }.orEmpty()

        if (exitCode != 0 || isError == true || subtype?.startsWith("error") == true) {
            val details = (listOfNotNull(result) + errors).filter { it.isNotBlank() }.joinToString("\n")
            throw failure(
                "Claude failed (exit code $exitCode${subtype?.let { ", $it" }.orEmpty()}).",
                details.ifBlank { stdout }, stderr
            )
        }
        if (type != "result" || subtype != "success" || isError != false || result == null) {
            throw failure("Claude returned an invalid response. Expected a JSON result.", stdout, stderr)
        }
        if (result.isBlank()) {
            throw failure("Claude returned an empty commit message. Please try again.", "", stderr)
        }
        return result
    }

    private fun JsonElement?.stringValue(): String? =
        this?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun failure(summary: String, output: String, stderr: String): RuntimeException {
        val details = listOf(output.trim(), stderr.trim()).filter { it.isNotEmpty() }.distinct()
        // Classify before truncation, including errors that appear after lengthy diagnostics.
        val tooLarge = details.any { detail ->
            listOf(
                "prompt is too long", "prompt too long", "request too large", "request_too_large",
                "payload too large", "context window", "context length", "context_length",
                "maximum context", "too many tokens", "input is too long", "input too long",
                "stdin input exceeds", "request size exceeds"
            ).any { detail.contains(it, ignoreCase = true) }
        }
        val explanation = if (tooLarge) {
            "Claude could not process the selected changes because the input exceeds its size or context limit.\n" +
                "Select fewer files or hunks, or split the changes into smaller commits."
        } else {
            summary
        }
        val diagnostics = details.joinToString("\n\n") {
            if (it.length > 1500) it.take(1500) + "\n… (truncated)" else it
        }
        return RuntimeException(
            explanation + if (diagnostics.isBlank()) "" else "\n\nClaude details:\n$diagnostics"
        )
    }
}
