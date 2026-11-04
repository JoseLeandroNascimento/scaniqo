plugins {
    id("scaniqo.android.library")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.joseleandro.scaniqo.core.common"
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(project(":core:domain"))
    implementation(project(":core:resources"))
}
