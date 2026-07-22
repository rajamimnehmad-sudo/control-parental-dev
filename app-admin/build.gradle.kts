plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.ksp)
}

fun envValue(name: String): String {
    val fromEnvironment = providers.environmentVariable(name).orNull
    if (!fromEnvironment.isNullOrBlank()) return fromEnvironment
    val envFile = rootProject.file(".env")
    if (!envFile.exists()) return ""
    return envFile
        .readLines()
        .firstOrNull { it.startsWith("$name=") }
        ?.substringAfter("=")
        ?.trim()
        .orEmpty()
}

val devSigningStorePath = providers.environmentVariable("ANDROID_DEV_KEYSTORE_PATH").orNull
val minSupportedApi = 29
val targetSupportedApi = 36
val intermediateSupportedApi = (minSupportedApi + targetSupportedApi) / 2

android {
    namespace = "com.contentfilter.admin"
    compileSdk = targetSupportedApi
    testBuildType = "compatibility"

    defaultConfig {
        applicationId = "com.contentfilter.admin"
        minSdk = minSupportedApi
        targetSdk = targetSupportedApi
        versionCode = 1
        versionName = "1.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "FIREBASE_APPLICATION_ID", "\"${envValue("FIREBASE_APPLICATION_ID")}\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${envValue("FIREBASE_API_KEY")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${envValue("FIREBASE_PROJECT_ID")}\"")
    }

    flavorDimensions += "distribution"

    signingConfigs {
        if (!devSigningStorePath.isNullOrBlank()) {
            create("devUpdate") {
                storeFile = file(devSigningStorePath)
                storePassword = envValue("ANDROID_DEV_KEYSTORE_PASSWORD")
                keyAlias = envValue("ANDROID_DEV_KEY_ALIAS")
                keyPassword = envValue("ANDROID_DEV_KEY_PASSWORD")
            }
        }
    }

    productFlavors {
        create("dev") {
            dimension = "distribution"
            applicationIdSuffix = ".dev"
            versionCode = 273
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

    buildTypes {
        debug {
            // DEV APKs are installed as updates on physical devices, but do
            // not need to expose a debuggable runtime outside local builds.
            isDebuggable = false
            if (!devSigningStorePath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("devUpdate")
            }
        }
        create("compatibility") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            isDebuggable = true
        }
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        managedDevices {
            devices {
                maybeCreate<com.android.build.api.dsl.ManagedVirtualDevice>("compatSmallApi29").apply {
                    device = "Pixel 2"
                    apiLevel = minSupportedApi
                    systemImageSource = "aosp"
                }
                maybeCreate<com.android.build.api.dsl.ManagedVirtualDevice>("compatPhoneApi32").apply {
                    device = "Pixel 6"
                    apiLevel = intermediateSupportedApi
                    systemImageSource = "aosp"
                }
                maybeCreate<com.android.build.api.dsl.ManagedVirtualDevice>("compatTabletApi36").apply {
                    device = "Pixel C"
                    apiLevel = targetSupportedApi
                    systemImageSource = "aosp"
                }
            }
        }
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
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    testImplementation(libs.kotlin.test)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
