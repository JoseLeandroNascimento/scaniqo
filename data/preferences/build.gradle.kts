plugins {
    id("scaniqo.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.joseleandro.scaniqo.data.preferences"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.koin.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(project(":core:domain"))
    implementation(project(":core:resources"))
}
