plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.kapt)
}

android {
    namespace = "com.contentfilter.user.dag2"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = false
        compose = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-network"))
    implementation(project(":feature-vpn"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.webkit)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    kapt(libs.hilt.compiler)
    testImplementation(libs.kotlin.test)
    testImplementation("org.json:json:20240303")
}

val dagV2ManifestFile = layout.projectDirectory.file("src/main/AndroidManifest.xml").asFile
val appUserBuildFile = rootProject.layout.projectDirectory.file("app-user/build.gradle.kts").asFile

val verifyDagV2Isolation by tasks.registering {
    group = "verification"
    description = "Fails if DAG v2 imports visual implementation details from DAG v1."
    inputs.files(
        fileTree("src") { include("**/*.kt") },
        dagV2ManifestFile,
        appUserBuildFile,
    )
    doLast {
        val forbiddenDagV1Types =
            listOf(
                "DagImageClassifier",
                "DagImageResourceLoader",
                "DagModestyImageClassifier",
                "DagProfessionalImageClassifier",
                "DagTzniutPoseClassifier",
                "DagImageCalibration",
            )
        val violations =
            inputs.files.files.filter { it.extension == "kt" }.flatMap { source ->
                forbiddenDagV1Types
                    .filter { forbidden -> source.readText().contains(forbidden) }
                    .map { forbidden -> "${source.path} imports or names $forbidden" }
            }
        check(violations.isEmpty()) {
            "DAG v2 isolation violations:\n${violations.joinToString("\n")}"
        }
        val sources = inputs.files.files.filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        check(!sources.contains("setWebContentsDebuggingEnabled(true)")) {
            "DAG v2 must not enable WebView debugging."
        }
        check(sources.contains("WebSettings.LOAD_NO_CACHE") && sources.contains("clearCache(true)")) {
            "DAG v2 DEV must expose a real no-cache WebView path."
        }
        val manifest =
            inputs.files.files
                .single { it.name == "AndroidManifest.xml" }
                .readText()
        check(manifest.contains("""android:exported="false"""")) {
            "DAG v2 Lab Activity must be internal."
        }
        check(!manifest.contains("<intent-filter>")) {
            "DAG v2 Lab Activity must not expose a launcher or external intent filter."
        }
        val appBuild =
            inputs.files.files
                .single { it.name == "build.gradle.kts" && it.path.contains("app-user") }
                .readText()
        check(appBuild.contains("""add("devImplementation", project(":feature-dag2"))""")) {
            "DAG v2 must remain a devImplementation dependency."
        }
        check(appBuild.contains("DAG_V2_BROWSER_AVAILABLE")) {
            "App Usuario must gate the internal DAG v2 entry by flavor."
        }
        check(appBuild.split("""DAG_V2_BROWSER_AVAILABLE", "true"""").size - 1 == 1) {
            "DAG v2 must be enabled in exactly one flavor."
        }
        check(appBuild.split("""DAG_V2_BROWSER_AVAILABLE", "false"""").size - 1 == 2) {
            "DAG v2 must be disabled in Beta and Production."
        }
        check(appBuild.split("""DAG_V2_CALIBRATION_AVAILABLE", "true"""").size - 1 == 1) {
            "DAG v2 calibration must be enabled only in DEV."
        }
        check(appBuild.split("""DAG_V2_CALIBRATION_AVAILABLE", "false"""").size - 1 == 2) {
            "DAG v2 calibration must be disabled in Beta and Production."
        }
    }
}

tasks.named("check").configure {
    dependsOn(verifyDagV2Isolation)
}
