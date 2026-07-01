plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.contentfilter.core.license"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
    }
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-data"))
    implementation(libs.kotlinx.coroutines.core)
}
