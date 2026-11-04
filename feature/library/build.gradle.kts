plugins {
    id("scaniqo.android.library")
    id("scaniqo.android.compose")
}

android {
    namespace = "com.joseleandro.scaniqo.feature.library"
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.koin.androidx.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(project(":core:ads"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:document-ui"))
    implementation(project(":core:domain"))
    implementation(project(":core:resources"))
    implementation(project(":core:security"))
    testImplementation(project(":core:testing"))
}
