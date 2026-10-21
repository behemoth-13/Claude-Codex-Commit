import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.7.1"
}

fun properties(key: String): String = providers.gradleProperty(key).get()

val flavor = providers.gradleProperty("flavor").getOrElse("claude")
require(flavor in setOf("claude", "codex")) { "Unknown flavor '$flavor'. Use claude or codex." }
val providerName = if (flavor == "codex") "Codex" else "Claude"
layout.buildDirectory.set(layout.projectDirectory.dir("build/$flavor"))
base {
    archivesName.set("$providerName-Commit")
}
sourceSets {
    main {
        resources.srcDir("src/$flavor/resources")
    }
}
kotlin.sourceSets.main {
    kotlin.srcDir("src/$flavor/kotlin")
}
kotlin.sourceSets.test {
    kotlin.srcDir("src/$flavor/test/kotlin")
}

group = properties("pluginGroup")
version = properties("pluginVersion")

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    intellijPlatform {
        create(properties("platformType"), properties("platformVersion"))
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        bundledPlugin("Git4Idea")
    }
}

intellijPlatform {
    buildSearchableOptions = false

    pluginConfiguration {
        name = "$providerName Commit"
        ideaVersion {
            sinceBuild = "251"
        }

        changeNotes = """
            Initial version
        """.trimIndent()
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
    patchPluginXml {
        inputFile.set(layout.projectDirectory.file("src/$flavor/resources/META-INF/plugin.xml"))
    }
    buildPlugin {
        archiveBaseName.set("$providerName-Commit")
        destinationDirectory.set(layout.projectDirectory.dir("build/distributions"))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}
