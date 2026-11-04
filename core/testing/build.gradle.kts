plugins {
    id("scaniqo.android.library")
}

android {
    namespace = "com.joseleandro.scaniqo.core.testing"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(project(":core:ads"))
    implementation(project(":core:domain"))
}
