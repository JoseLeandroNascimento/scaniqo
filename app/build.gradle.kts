plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

android {
    namespace = "com.joseleandro.scaniqo"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.joseleandro.scaniqo"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions.unitTests.isIncludeAndroidResources = true
}

room {
    schemaDirectory("$projectDir/schemas")
}

// PDFBox usa os mesmos assets do AAR no Android e como recursos de classpath nos testes JVM.
val pdfBoxUnitTestAssets = configurations.create("pdfBoxUnitTestAssets") {
    isCanBeConsumed = false
    isTransitive = false
}
dependencies.add(pdfBoxUnitTestAssets.name, libs.pdfbox.android)

val extractPdfBoxUnitTestAssets = tasks.register<Sync>("extractPdfBoxUnitTestAssets") {
    from({ zipTree(pdfBoxUnitTestAssets.singleFile) }) {
        include("assets/**")
        eachFile {
            relativePath = RelativePath(
                true,
                *relativePath.segments.drop(1).toTypedArray()
            )
        }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("generated/pdfboxUnitTestAssets"))
}
android.testOptions.unitTests.all { test ->
    test.dependsOn(extractPdfBoxUnitTestAssets)
    test.classpath += files(extractPdfBoxUnitTestAssets)
    // Robolectric usa APIs Android reais nos testes de campos e camadas PDF.
    test.jvmArgs(
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

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.google.auth)
    implementation(libs.mlkit.document.scanner)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.pdfbox.android)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.lifecycle.process)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric) {
        // PDFBox já fornece BC (jdk15to18). Duas variantes duplicam classes e quebram CMS.
        exclude(group = "org.bouncycastle", module = "bcprov-jdk18on")
    }
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}