plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.contentfilter.core.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
    }
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-database"))
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.android)
    kapt(libs.hilt.compiler)
}
