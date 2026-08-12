plugins {
    id("com.android.application") version "8.9.3"
    id("org.jetbrains.kotlin.android") version "2.3.21"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
}

fun envValue(name: String): String {
    val fromEnvironment = providers.environmentVariable(name).orNull
    if (!fromEnvironment.isNullOrBlank()) return fromEnvironment
    val envFiles = listOf(rootProject.file(".env"), rootProject.file("../.env")).distinct()
    return envFiles
        .firstNotNullOfOrNull { envFile ->
            envFile
                .takeIf { it.isFile }
                ?.readLines()
                ?.firstOrNull { it.startsWith("$name=") }
                ?.substringAfter("=")
                ?.trim()
        }.orEmpty()
}

fun buildConfigString(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

val diagnosticUploadUrl =
    envValue("DAG_DIAGNOSTIC_UPLOAD_URL").ifBlank {
        "https://syeycayasyufedwoprea.supabase.co/functions/v1/dag-diagnostic-report"
    }
val diagnosticUploadToken = envValue("DAG_DIAGNOSTIC_UPLOAD_TOKEN")

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
        versionCode = 212
        versionName = "0.70.16"
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
            buildConfigField("boolean", "DAG_DIAGNOSTICS", "false")
            buildConfigField("String", "DAG_DIAGNOSTIC_UPLOAD_URL", buildConfigString(diagnosticUploadUrl))
            buildConfigField("String", "DAG_DIAGNOSTIC_UPLOAD_TOKEN", buildConfigString(diagnosticUploadToken))
        }
        create("diagnostic") {
            dimension = "distribution"
            applicationIdSuffix = ".diagnostic.dev"
            versionCode = 11
            versionNameSuffix = "-diagnostic"
            resValue("string", "app_name", "DAG Browser Diagnostic")
            buildConfigField("boolean", "DAG_DIAGNOSTICS", "true")
            buildConfigField("String", "DAG_DIAGNOSTIC_UPLOAD_URL", buildConfigString(diagnosticUploadUrl))
            buildConfigField("String", "DAG_DIAGNOSTIC_UPLOAD_TOKEN", buildConfigString(diagnosticUploadToken))
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

val verifyDagDiagnosticUploadConfig =
    tasks.register("verifyDagDiagnosticUploadConfig") {
        group = "verification"
        description = "Fails APK packaging when the private DAG diagnostic upload channel is missing."
        inputs.property("uploadUrl", diagnosticUploadUrl)
        inputs.property("uploadTokenLength", diagnosticUploadToken.length)
        doLast {
            val uploadUrl = inputs.properties.getValue("uploadUrl") as String
            val uploadTokenLength = inputs.properties.getValue("uploadTokenLength") as Int
            check(uploadUrl.startsWith("https://")) {
                "DAG_DIAGNOSTIC_UPLOAD_URL must use HTTPS"
            }
            check(uploadTokenLength >= 32) {
                "DAG_DIAGNOSTIC_UPLOAD_TOKEN is missing; refusing to package a diagnostic-enabled APK"
            }
        }
    }

tasks.matching { it.name == "packageDevDebug" || it.name == "packageDiagnosticDebug" }
    .configureEach { dependsOn(verifyDagDiagnosticUploadConfig) }

ktlint {
    android.set(true)
    outputToConsole.set(true)
    ignoreFailures.set(false)
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.4.0")
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
            "src/main/assets/dag-protection/ads.js",
            "src/main/assets/dag-protection/background.js",
            "src/main/assets/dag-protection/barrier.js",
            "src/main/assets/dag-protection/barrier.css",
            "src/main/assets/dag-protection/manifest.json",
            "src/main/assets/dag-protection/runaway-scheduler-guard.js",
            "src/main/assets/dag-protection/video-lab-fixture.html",
            "src/main/assets/dag-protection/video-lab-fixture.js",
            "src/main/assets/dag-protection/video-lab.js",
            "src/test/js/dag-protection.test.mjs",
        )
    }

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    inputs.dir("src/main/assets/dag-protection")
}

tasks.named("check") {
    dependsOn(testDagProtectionJs)
}
