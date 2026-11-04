plugins {
    id("scaniqo.android.library")
    alias(libs.plugins.kotlin.serialization)
    id("scaniqo.pdfbox.testing")
}

android {
    namespace = "com.joseleandro.scaniqo.data.backup"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime)
    implementation(libs.google.auth)
    implementation(libs.koin.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.pdfbox.android)
    implementation(project(":core:domain"))
    implementation(project(":core:resources"))
    implementation(project(":data:pdf"))
    testImplementation(libs.pdfbox.android)
}
