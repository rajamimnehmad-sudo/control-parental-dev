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
    return envFile.readLines().firstOrNull { it.startsWith("$name=") }?.substringAfter("=")?.trim().orEmpty()
}

val devSigningStorePath = providers.environmentVariable("ANDROID_DEV_KEYSTORE_PATH").orNull
val minSupportedApi = 29
val targetSupportedApi = 36
val intermediateSupportedApi = (minSupportedApi + targetSupportedApi) / 2

android {
    namespace = "com.contentfilter.user"
    compileSdk = targetSupportedApi
    testBuildType = "compatibility"

    defaultConfig {
        applicationId = "com.contentfilter.user"
        minSdk = minSupportedApi
        targetSdk = targetSupportedApi
        versionCode = 1
        versionName = "1.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "FIREBASE_APPLICATION_ID", "\"${envValue("FIREBASE_APPLICATION_ID")}\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${envValue("FIREBASE_API_KEY")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${envValue("FIREBASE_PROJECT_ID")}\"")
        ndk {
            // Standard Android phones from Samsung, Xiaomi, Motorola and Oppo
            // use ARM. Excluding emulator-only x86 keeps the local model update small.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
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
            versionCode = 277
            versionNameSuffix = "-dev"
            buildConfigField(
                "String",
                "DAG_NEURAL_MODEL_URL",
                "\"https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates/dag-models/\"",
            )
            buildConfigField("boolean", "DAG_VISUAL_CALIBRATION_AVAILABLE", "true")
        }
        create("beta") {
            dimension = "distribution"
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            buildConfigField("String", "DAG_NEURAL_MODEL_URL", "\"\"")
            buildConfigField("boolean", "DAG_VISUAL_CALIBRATION_AVAILABLE", "false")
        }
        create("prod") {
            dimension = "distribution"
            buildConfigField("String", "DAG_NEURAL_MODEL_URL", "\"\"")
            buildConfigField("boolean", "DAG_VISUAL_CALIBRATION_AVAILABLE", "false")
        }
    }

    buildTypes {
        debug {
            // DEV is distributed to physical test devices. Keeping the debug
            // signing key allows in-place updates, while disabling JDWP avoids
            // the runtime cost of a debuggable Compose/WebView process.
            isDebuggable = false
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (!devSigningStorePath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("devUpdate")
            }
        }
        create("compatibility") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            isDebuggable = true
            isMinifyEnabled = false
            ndk {
                // Test-only ABI for x86_64 Android emulators. DEV stays ARM-only.
                abiFilters += "x86_64"
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    androidResources {
        noCompress += "tflite"
    }

    packaging {
        jniLibs {
            // Keep the app and the existing TFLite image model compatible with
            // ARM32. The much larger neural text runtime is ARM64-only; ARM32
            // devices safely retain the compact local text classifier.
            excludes +=
                setOf(
                    "lib/armeabi-v7a/libonnxruntime.so",
                    "lib/armeabi-v7a/libonnxruntime4j_jni.so",
                    "lib/armeabi-v7a/libonnxruntime_extensions4j_jni.so",
                    "lib/armeabi-v7a/libortextensions.so",
                )
        }
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
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.hilt.android)
    implementation(libs.okhttp)
    implementation(libs.onnxruntime.android)
    implementation(libs.onnxruntime.extensions.android)
    implementation(libs.tflite)
    ksp(libs.androidx.hilt.compiler)
    kapt(libs.hilt.compiler)
    testImplementation(libs.kotlin.test)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
