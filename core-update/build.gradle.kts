plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.kapt)
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

android {
    namespace = "com.contentfilter.core.update"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
        buildConfigField(
            "String",
            "UPDATE_MANIFEST_URL",
            "\"${envValue("UPDATE_MANIFEST_URL")}\"",
        )
        buildConfigField(
            "String",
            "UPDATE_MANIFEST_URL_USER",
            "\"${envValue("UPDATE_MANIFEST_URL_USER")}\"",
        )
        buildConfigField(
            "String",
            "UPDATE_MANIFEST_URL_ADMIN",
            "\"${envValue("UPDATE_MANIFEST_URL_ADMIN")}\"",
        )
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core-domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.hilt.android)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlin.test)
    kapt(libs.hilt.compiler)
}
