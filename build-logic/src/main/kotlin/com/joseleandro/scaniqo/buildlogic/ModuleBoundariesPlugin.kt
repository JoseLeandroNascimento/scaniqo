package com.joseleandro.scaniqo.buildlogic

import java.io.Serializable
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.work.DisableCachingByDefault

class ModuleBoundariesPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val declarations = objects.listProperty(ModuleDependencyDeclaration::class.java)
            .convention(emptyList())
        configurations.configureEach {
            val configurationName = name
            dependencies.withType<ProjectDependency>().all {
                declarations.add(ModuleDependencyDeclaration(configurationName, path))
            }
        }
        val boundaries = tasks.register<CheckModuleBoundariesTask>("checkModuleBoundaries") {
            group = "verification"
            description = "Checks dependency direction between application, core, data and feature modules."
            sourceModulePath.set(target.path)
            declaredDependencies.set(declarations)
        }
        tasks.matching { it.name == "check" || it.name == "preBuild" }.configureEach {
            dependsOn(boundaries)
        }
    }
}

data class ModuleDependencyDeclaration(
    @get:Input val configurationName: String,
    @get:Input val targetPath: String,
) : Serializable

@DisableCachingByDefault(because = "This task validates dependency declarations and produces no files.")
abstract class CheckModuleBoundariesTask : DefaultTask() {
    @get:Input abstract val sourceModulePath: Property<String>
    @get:Nested abstract val declaredDependencies: ListProperty<ModuleDependencyDeclaration>

    @TaskAction
    fun checkBoundaries() {
        val violations = declaredDependencies.get().flatMap { dependency ->
            ModuleDependencyRules.violations(
                sourceModulePath.get(), listOf(dependency.targetPath), dependency.configurationName,
            ).map { "$it [${dependency.configurationName}]" }
        }.distinct().sorted()
        if (violations.isNotEmpty()) {
            throw GradleException("Invalid module dependencies:\n" + violations.joinToString("\n"))
        }
    }
}
