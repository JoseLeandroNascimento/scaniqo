package com.joseleandro.scaniqo.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun Project.addLibrary(configuration: String, alias: String) {
    dependencies.add(configuration, libs.findLibrary(alias).get())
}
