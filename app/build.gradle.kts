import java.io.File

plugins {
    alias(libs.plugins.android.application)
    id("scaniqo.android.compose")
    id("scaniqo.module.boundaries")
    id("scaniqo.pdfbox.testing")
    alias(libs.plugins.kotlin.serialization)
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
val playLicenseKey = providers.gradleProperty("scaniqo.play.licenseKey")
    .orElse("").get().replace(Regex("\\s"), "")
require(playLicenseKey.isEmpty() || Regex("[A-Za-z0-9+/=]+").matches(playLicenseKey)) {
    "scaniqo.play.licenseKey must contain only the Base64 public licensing key from Play Console."
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
        versionCode = 5
        versionName = "1.0.5"
        resValue("string", "scaniqo_play_license_key", playLicenseKey)
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

// Heavy fixtures are opt-in so everyday unit tests do not create hundreds of MiB of data.
val runLoadTests = providers.gradleProperty("scaniqo.loadTests").map(String::toBoolean).orElse(false)
android.testOptions.unitTests.all { test ->
    val resourceDirectories = rootProject.subprojects.map {
        it.layout.projectDirectory.dir("src/main/res").asFile
    }
    test.systemProperty("scaniqo.resourceDirectories", resourceDirectories.joinToString(File.pathSeparator))
    test.inputs.files(resourceDirectories).withPropertyName("moduleResourceCatalogs")
    if (!runLoadTests.get()) {
        test.filter.excludeTestsMatching("*LoadTest")
    } else {
        test.maxParallelForks = 1
        test.maxHeapSize = "512m"
        test.testLogging.showStandardStreams = true
    }
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
    implementation(project(":core:domain"))
    implementation(project(":core:resources"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:security"))
    implementation(project(":core:ads"))
    implementation(project(":core:billing"))
    implementation(project(":feature:premium"))
    implementation(project(":core:document-ui"))
    implementation(project(":data:documents"))
    implementation(project(":data:pdf"))
    implementation(project(":data:preferences"))
    implementation(project(":data:signatures"))
    implementation(project(":data:backup"))
    implementation(project(":feature:library"))
    implementation(project(":feature:editor"))
    implementation(project(":feature:signatures"))
    implementation(project(":feature:merge"))
    implementation(project(":feature:pdf-tools"))
    implementation(project(":feature:backup"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:onboarding"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.window)
    implementation(libs.androidx.adaptive)
    implementation(libs.androidx.navigation.suite)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.mlkit.document.scanner)
    implementation(libs.pdfbox.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric) {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk18on")
    }
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
