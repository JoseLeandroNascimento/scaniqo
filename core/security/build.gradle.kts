plugins {
    id("scaniqo.android.library")
    id("scaniqo.android.compose")
}

android {
    namespace = "com.joseleandro.scaniqo.core.security"
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.koin.androidx.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(project(":core:designsystem"))
    implementation(project(":core:resources"))
}
