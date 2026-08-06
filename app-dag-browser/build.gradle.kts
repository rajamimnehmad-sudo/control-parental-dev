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
        versionCode = 109
        versionName = "0.69.13"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            // The direct-install APK targets modern 64-bit Android phones. Additional ABIs must
            // ship as separate artifacts so every user does not pay the size of every runtime.
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
            buildConfigField("boolean", "GLOSHIA_VISUAL_ENABLED", "true")
            buildConfigField("boolean", "GLOSHIA_LAB_FIXTURE", "false")
        }
        create("lab") {
            dimension = "distribution"
            applicationIdSuffix = ".lab"
            versionCode = 111
            versionNameSuffix = "-lab"
            buildConfigField("boolean", "GLOSHIA_VISUAL_ENABLED", "true")
            buildConfigField("boolean", "GLOSHIA_LAB_FIXTURE", "true")
        }
    }

    buildFeatures {
        buildConfig = true
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

    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}

val testDagProtectionJs =
    tasks.register<Exec>("testDagProtectionJs") {
        group = "verification"
        description = "Runs the network-free DAG WebExtension security harness."
        workingDir = projectDir
        commandLine("node", "--test", "src/test/js/dag-protection.test.mjs")
        inputs.files(
            "src/main/assets/dag-protection/background.js",
            "src/main/assets/dag-protection/barrier.js",
            "src/main/assets/dag-protection/barrier.css",
            "src/test/js/dag-protection.test.mjs",
        )
    }

tasks.named("check") {
    dependsOn(testDagProtectionJs)
}
