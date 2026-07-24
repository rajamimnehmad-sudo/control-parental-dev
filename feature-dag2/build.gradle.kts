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
}

val verifyDagV2Isolation by tasks.registering {
    group = "verification"
    description = "Fails if DAG v2 imports visual implementation details from DAG v1."
    inputs.files(fileTree("src") { include("**/*.kt") })
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
            inputs.files.files.flatMap { source ->
                forbiddenDagV1Types
                    .filter { forbidden -> source.readText().contains(forbidden) }
                    .map { forbidden -> "${source.path} imports or names $forbidden" }
            }
        check(violations.isEmpty()) {
            "DAG v2 isolation violations:\n${violations.joinToString("\n")}"
        }
    }
}

tasks.named("check").configure {
    dependsOn(verifyDagV2Isolation)
}
