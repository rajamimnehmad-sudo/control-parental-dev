plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.contentfilter.user"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.contentfilter.user"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.1"
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("dev") {
            dimension = "distribution"
            applicationIdSuffix = ".dev"
            versionCode = 129
            versionNameSuffix = "-dev"
        }
        create("beta") {
            dimension = "distribution"
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
        }
        create("prod") {
            dimension = "distribution"
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-data"))
    implementation(project(":core-database"))
    implementation(project(":core-network"))
    implementation(project(":core-security"))
    implementation(project(":core-sync"))
    implementation(project(":core-telemetry"))
    implementation(project(":core-update"))
    implementation(project(":core-ui"))
    implementation(project(":feature-status"))
    implementation(project(":feature-onboarding"))
    implementation(project(":feature-vpn"))
    implementation(project(":feature-accessibility"))
    implementation(project(":feature-activation"))
    implementation(project(":feature-requests"))
    implementation(project(":feature-usage"))
    implementation(project(":feature-block"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
}
