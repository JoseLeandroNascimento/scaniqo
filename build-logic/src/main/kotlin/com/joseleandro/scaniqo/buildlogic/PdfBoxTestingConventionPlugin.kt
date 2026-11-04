package com.joseleandro.scaniqo.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.RelativePath
import javax.inject.Inject
import org.gradle.api.tasks.Sync
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register

class PdfBoxTestingConventionPlugin @Inject constructor(
    private val archiveOperations: ArchiveOperations,
) : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val assets = configurations.create("pdfBoxUnitTestAssets") {
            isCanBeConsumed = false
            isCanBeResolved = true
            isTransitive = false
        }
        dependencies.add(assets.name, libs.findLibrary("pdfbox-android").get())
        val archive = assets.elements.map { elements -> elements.single().asFile }
        val assetDirectory = layout.buildDirectory.dir("generated/pdfboxUnitTestAssets")
        val extractAssets = tasks.register<Sync>("extractPdfBoxUnitTestAssets") {
            from(archive.map { archiveOperations.zipTree(it) }) {
                include("assets/**")
                eachFile {
                    relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
                }
                includeEmptyDirs = false
            }
            into(assetDirectory)
        }
        val assetClasspath = files(assetDirectory).builtBy(extractAssets)
        // AGP replaces the initial Test classpath while creating each variant. Its unit-test
        // callback runs afterwards, so the extracted resources stay on the final classpath.
        pluginManager.withPlugin("com.android.application") {
            extensions.configure<ApplicationExtension> {
                testOptions.unitTests.all { test ->
                    test.dependsOn(extractAssets)
                    test.classpath += assetClasspath
                }
            }
        }
        pluginManager.withPlugin("com.android.library") {
            extensions.configure<LibraryExtension> {
                testOptions.unitTests.all { test ->
                    test.dependsOn(extractAssets)
                    test.classpath += assetClasspath
                }
            }
        }
    }
}
