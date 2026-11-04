plugins {
    id("scaniqo.jvm.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.core)
    testImplementation(libs.kotlinx.serialization.json)
}
