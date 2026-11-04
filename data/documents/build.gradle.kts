plugins {
    id("scaniqo.android.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
    id("scaniqo.pdfbox.testing")
}

android {
    namespace = "com.joseleandro.scaniqo.data.documents"
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(libs.androidx.room.runtime)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.koin.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:resources"))
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.pdfbox.android)
}
