rootProject.name = if (providers.gradleProperty("flavor").getOrElse("claude") == "codex") {
    "Codex-Commit"
} else {
    "Claude-Commit"
}
