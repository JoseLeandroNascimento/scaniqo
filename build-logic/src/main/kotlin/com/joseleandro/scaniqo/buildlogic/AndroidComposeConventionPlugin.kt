package com.joseleandro.scaniqo.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.withPlugin("com.android.library") { configureAndroidCompose() }
        pluginManager.withPlugin("com.android.application") { configureAndroidCompose() }
    }

    private fun Project.configureAndroidCompose() {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        extensions.configure<CommonExtension> {
            buildFeatures.compose = true
        }
        dependencies.add("implementation", dependencies.platform(libs.findLibrary("androidx-compose-bom").get()))
        dependencies.add("androidTestImplementation", dependencies.platform(libs.findLibrary("androidx-compose-bom").get()))
        addLibrary("implementation", "androidx-compose-ui")
        addLibrary("implementation", "androidx-compose-ui-graphics")
        addLibrary("implementation", "androidx-compose-material3")
        addLibrary("implementation", "androidx-compose-ui-tooling-preview")
        addLibrary("androidTestImplementation", "androidx-compose-ui-test-junit4")
        addLibrary("debugImplementation", "androidx-compose-ui-test-manifest")
        addLibrary("debugImplementation", "androidx-compose-ui-tooling")
    }
}
