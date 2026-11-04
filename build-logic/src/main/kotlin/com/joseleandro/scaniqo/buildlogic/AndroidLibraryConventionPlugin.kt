package com.joseleandro.scaniqo.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("scaniqo.module.boundaries")
        extensions.configure<LibraryExtension> {
            compileSdk {
                version = release(37)
            }
            defaultConfig {
                minSdk = 24
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_11
                targetCompatibility = JavaVersion.VERSION_11
            }
            testOptions.unitTests.isIncludeAndroidResources = true
        }
        addLibrary("testImplementation", "junit")
        addLibrary("testImplementation", "kotlinx-coroutines-test")
        val robolectric = libs.findLibrary("robolectric").get().get()
        val robolectricDependency = dependencies.create(
            "${robolectric.module.group}:${robolectric.module.name}:${robolectric.versionConstraint.requiredVersion}",
        ) as ExternalModuleDependency
        robolectricDependency.exclude(mapOf("group" to "org.bouncycastle", "module" to "bcprov-jdk18on"))
        dependencies.add("testImplementation", robolectricDependency)
        addLibrary("testRuntimeOnly", "bouncycastle-provider")
        addLibrary("androidTestImplementation", "androidx-junit")
        addLibrary("androidTestImplementation", "androidx-espresso-core")
        configureUnitTests()
    }
}
