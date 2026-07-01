plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.contentfilter.core.ui"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
    }
}

dependencies {
    implementation(project(":core-domain"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
}
