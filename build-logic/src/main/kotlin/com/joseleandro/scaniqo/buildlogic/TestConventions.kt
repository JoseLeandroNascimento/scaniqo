package com.joseleandro.scaniqo.buildlogic

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType

internal fun Project.configureUnitTests() {
    val runLoadTests = providers.gradleProperty("scaniqo.loadTests").map(String::toBoolean).orElse(false)
    tasks.withType<Test>().configureEach {
        useJUnit()
        if (!runLoadTests.get()) {
            filter.excludeTestsMatching("*LoadTest")
        } else {
            maxParallelForks = 1
            maxHeapSize = "512m"
            testLogging.showStandardStreams = true
        }
        jvmArgs(
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED",
            "--add-opens=java.base/java.io=ALL-UNNAMED",
            "--add-opens=java.base/java.net=ALL-UNNAMED",
            "--add-opens=java.base/java.security=ALL-UNNAMED",
            "--add-opens=java.base/java.text=ALL-UNNAMED",
            "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
            "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
        )
    }
}
