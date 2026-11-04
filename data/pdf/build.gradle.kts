plugins {
    id("scaniqo.android.library")
    alias(libs.plugins.kotlin.serialization)
    id("scaniqo.pdfbox.testing")
}

android {
    namespace = "com.joseleandro.scaniqo.data.pdf"
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.koin.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.pdfbox.android)
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:resources"))
    implementation(project(":data:documents"))
    testImplementation(libs.pdfbox.android)
    testImplementation(project(":data:signatures"))
}
