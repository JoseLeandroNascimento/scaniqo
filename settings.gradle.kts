pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Scaniqo"
include(
    ":app",
    ":core:domain",
    ":core:resources",
    ":core:common",
    ":core:designsystem",
    ":core:security",
    ":core:ads",
    ":core:document-ui",
    ":core:testing",
    ":data:documents",
    ":data:pdf",
    ":data:preferences",
    ":data:signatures",
    ":data:backup",
    ":feature:library",
    ":feature:editor",
    ":feature:signatures",
    ":feature:merge",
    ":feature:pdf-tools",
    ":feature:backup",
    ":feature:settings",
    ":feature:onboarding",
)
