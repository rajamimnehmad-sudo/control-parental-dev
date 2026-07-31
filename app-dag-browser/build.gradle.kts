plugins {
    id("com.android.application") version "8.9.3"
    id("org.jetbrains.kotlin.android") version "2.3.21"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
}

fun envValue(name: String): String {
    val fromEnvironment = providers.environmentVariable(name).orNull
    if (!fromEnvironment.isNullOrBlank()) return fromEnvironment
    val envFile = rootProject.file(".env")
    if (!envFile.exists()) return ""
    return envFile.readLines().firstOrNull { it.startsWith("$name=") }?.substringAfter("=")?.trim().orEmpty()
}

val devSigningStorePath = providers.environmentVariable("ANDROID_DEV_KEYSTORE_PATH").orNull

android {
    namespace = "com.contentfilter.dagbrowser"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "com.contentfilter.dagbrowser"
        minSdk = 29
        targetSdk = 36
        versionCode = 45
        versionName = "0.27.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            // The first physical gate targets the current 64-bit Samsung devices.
            // Other ABIs are added only after the browser foundation passes.
            abiFilters += "arm64-v8a"
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
            versionNameSuffix = "-dev"
        }
    }

    buildTypes {
        debug {
            isDebuggable = false
            isMinifyEnabled = false
            if (!devSigningStorePath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("devUpdate")
            }
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = false
    }

    packaging {
        jniLibs.useLegacyPackaging = true
    }
}

ktlint {
    android.set(true)
    outputToConsole.set(true)
    ignoreFailures.set(false)
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.27.0")
    implementation("org.mozilla.geckoview:geckoview-arm64-v8a:153.0.20260715202819")

    testImplementation(kotlin("test"))
    testImplementation("org.json:json:20250517")
}
