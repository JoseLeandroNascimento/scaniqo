plugins {
    id("scaniqo.android.library")
}

android {
    namespace = "com.joseleandro.scaniqo.core.billing"
}

dependencies {
    implementation(libs.google.play.billing)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.koin.android)
    implementation(project(":core:domain"))
}
