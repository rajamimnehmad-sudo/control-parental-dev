plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.contentfilter.core.sync"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
    }
}

kapt {
    correctErrorTypes = true
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-data"))
    implementation(project(":core-database"))
    implementation(project(":core-network"))
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.android)
    ksp(libs.androidx.hilt.compiler)
    kapt(libs.hilt.compiler)
}
