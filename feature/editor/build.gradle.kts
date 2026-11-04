plugins {
    id("scaniqo.android.library")
    id("scaniqo.android.compose")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.joseleandro.scaniqo.feature.editor"
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.koin.androidx.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.mlkit.document.scanner)
    implementation(project(":core:ads"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:document-ui"))
    implementation(project(":core:domain"))
    implementation(project(":core:resources"))
    implementation(project(":core:security"))
    implementation(project(":feature:signatures"))
}
