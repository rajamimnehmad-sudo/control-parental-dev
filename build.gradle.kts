plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

subprojects {
    pluginManager.apply("io.gitlab.arturbosch.detekt")
    pluginManager.apply("org.jlleitschuh.gradle.ktlint")

    plugins.withId("com.android.application") {
        extensions.configure<com.android.build.gradle.BaseExtension>("android") {
            buildToolsVersion("36.0.0")
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            lintOptions {
                disable.add("NullSafeMutableLiveData")
            }
        }
    }

    plugins.withId("com.android.library") {
        extensions.configure<com.android.build.gradle.BaseExtension>("android") {
            buildToolsVersion("36.0.0")
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            lintOptions {
                disable.add("NullSafeMutableLiveData")
            }
        }
    }

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension>("detekt") {
        buildUponDefaultConfig = true
        allRules = false
        ignoreFailures = true
        config.setFrom(rootProject.files("build-logic/config/detekt.yml"))
    }

    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension>("ktlint") {
        android.set(true)
        outputToConsole.set(true)
        ignoreFailures.set(false)
    }
}

val prepareDevUpdatesForStorage by tasks.registering(Exec::class) {
    group = "dev updates"
    description = "Prepara APKs y manifiestos DEV para Supabase Storage."
    notCompatibleWithConfigurationCache("Genera artefactos locales de publicacion DEV.")
    workingDir = rootProject.rootDir
    commandLine("scripts/prepare_dev_updates.sh")
}

val uploadDevUpdatesToStorage by tasks.registering(Exec::class) {
    group = "dev updates"
    description = "Publica APKs y manifiestos DEV en Supabase Storage."
    notCompatibleWithConfigurationCache("Usa Supabase CLI y estado de tareas locales para publicar DEV.")
    dependsOn(prepareDevUpdatesForStorage)
    workingDir = rootProject.rootDir
    commandLine("scripts/upload_dev_updates.sh")
}

gradle.projectsEvaluated {
    val userDevDebug = project(":app-user").tasks.named("assembleDevDebug")
    val adminDevDebug = project(":app-admin").tasks.named("assembleDevDebug")

    prepareDevUpdatesForStorage.configure {
        dependsOn(userDevDebug, adminDevDebug)
    }
}
