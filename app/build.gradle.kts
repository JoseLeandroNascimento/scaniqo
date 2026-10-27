plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

fun productionAdUnit(propertyName: String): String {
    val unitId = providers.gradleProperty(propertyName).orElse("").get().trim()
    require(unitId.isEmpty() || Regex("ca-app-pub-[0-9]{16}/[0-9]{10}").matches(unitId)) {
        "$propertyName must be an AdMob ad unit ID containing /, not an app ID containing ~."
    }
    return unitId
}

val productionBannerUnitId = productionAdUnit("scaniqo.admob.bannerUnitId")
val productionEditorBannerUnitId = productionAdUnit("scaniqo.admob.editorBannerUnitId")
val productionDocumentsNativeUnitId = productionAdUnit("scaniqo.admob.documentsNativeUnitId")
val productionToolsRewardedUnitId = productionAdUnit("scaniqo.admob.toolsRewardedUnitId")

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
        resValue("string", "admob_app_id", "ca-app-pub-7067924995647630~4025082822")
        resValue("string", "admob_banner_unit_id", productionBannerUnitId)
        resValue("string", "admob_editor_banner_unit_id", productionEditorBannerUnitId)
        resValue("string", "admob_documents_native_unit_id", productionDocumentsNativeUnitId)
        resValue("string", "admob_tools_rewarded_unit_id", productionToolsRewardedUnitId)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            resValue("string", "admob_app_id", "ca-app-pub-3940256099942544~3347511713")
            resValue("string", "admob_banner_unit_id", "ca-app-pub-3940256099942544/9214589741")
            resValue("string", "admob_editor_banner_unit_id", "ca-app-pub-3940256099942544/6300978111")
            resValue("string", "admob_documents_native_unit_id", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "admob_tools_rewarded_unit_id", "ca-app-pub-3940256099942544/5224354917")
        }
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
        resValues = true
    }
    androidResources {
        localeFilters += listOf("en", "pt", "es", "fr", "hi", "in", "bn", "zh-rCN", "ar", "ru")
    }
    // All supported languages remain available offline in the in-app language picker.
    bundle.language.enableSplit = false
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
// Heavy fixtures are opt-in so everyday unit tests do not create hundreds of MiB of data.
val runLoadTests = providers.gradleProperty("scaniqo.loadTests").map(String::toBoolean).orElse(false)
android.testOptions.unitTests.all { test ->
    if (!runLoadTests.get()) {
        test.filter.excludeTestsMatching("*LoadTest")
    } else {
        test.maxParallelForks = 1
        test.maxHeapSize = "512m"
        test.testLogging.showStandardStreams = true
    }
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
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.google.auth)
    implementation(libs.google.mobile.ads)
    implementation(libs.google.ump)
    implementation(libs.androidx.work.runtime)
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