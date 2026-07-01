plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
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

android {
    namespace = "com.contentfilter.core.network"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"${envValue("SUPABASE_URL")}\"",
        )
        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            "\"${envValue("SUPABASE_ANON_KEY")}\"",
        )
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core-domain"))
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    kapt(libs.hilt.compiler)
}
