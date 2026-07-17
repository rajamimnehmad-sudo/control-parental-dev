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

android {
    namespace = "com.contentfilter.user"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.contentfilter.user"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.1"
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
    productFlavors {
        create("dev") {
            dimension = "distribution"
            applicationIdSuffix = ".dev"
            versionCode = 255
            versionNameSuffix = "-dev"
            buildConfigField(
                "String",
                "DAG_NEURAL_MODEL_URL",
                "\"https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates/dag-models/\"",
            )
        }
        create("beta") {
            dimension = "distribution"
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            buildConfigField("String", "DAG_NEURAL_MODEL_URL", "\"\"")
        }
        create("prod") {
            dimension = "distribution"
            buildConfigField("String", "DAG_NEURAL_MODEL_URL", "\"\"")
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
    kapt(libs.hilt.compiler)
    testImplementation(libs.kotlin.test)
    testImplementation("org.json:json:20240303")
}
