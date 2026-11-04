import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "com.joseleandro.scaniqo.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.compose.gradle.plugin)
    testImplementation(libs.junit)
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "scaniqo.android.library"
            implementationClass = "com.joseleandro.scaniqo.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "scaniqo.android.compose"
            implementationClass = "com.joseleandro.scaniqo.buildlogic.AndroidComposeConventionPlugin"
        }
        register("jvmLibrary") {
            id = "scaniqo.jvm.library"
            implementationClass = "com.joseleandro.scaniqo.buildlogic.JvmLibraryConventionPlugin"
        }
        register("pdfboxTesting") {
            id = "scaniqo.pdfbox.testing"
            implementationClass = "com.joseleandro.scaniqo.buildlogic.PdfBoxTestingConventionPlugin"
        }
        register("moduleBoundaries") {
            id = "scaniqo.module.boundaries"
            implementationClass = "com.joseleandro.scaniqo.buildlogic.ModuleBoundariesPlugin"
        }
    }
}
