plugins {
    id("com.android.application")
}

val benchmarkCache =
    providers.environmentVariable("DAG_V2_BENCHMARK_CACHE").orElse(
        providers.systemProperty("user.home").map { "$it/.cache/dag-v2-benchmark" },
    )
val generatedAssets = layout.buildDirectory.dir("generated/benchmarkAssets")
val benchmarkTool = rootProject.projectDir.parentFile.resolve("dag_v2_benchmark.py")

val validateBenchmarkCache by tasks.registering(Exec::class) {
    commandLine(
        "python3",
        benchmarkTool.absolutePath,
        "--cache",
        benchmarkCache.get(),
        "verify-android-assets",
    )
}

val prepareBenchmarkAssets by tasks.registering(Sync::class) {
    dependsOn(validateBenchmarkCache)
    val cache = benchmarkCache.get()
    from("$cache/models") {
        include(
            "nsfw_marqo_vit_tiny_384.onnx",
            "pose_landmarker_lite.task",
            "selfie_multiclass_256x256.tflite",
        )
        into("models")
    }
    from("$cache/android-subset") {
        exclude("manifest.json")
        into("corpus")
    }
    from("$cache/android-subset/manifest.json") {
        into("corpus")
    }
    into(generatedAssets)
    doLast {
        val modelFiles = fileTree(generatedAssets.map { it.dir("models") }).files
        val corpusFiles =
            fileTree(generatedAssets.map { it.dir("corpus") })
                .matching { exclude("manifest.json") }
                .files
        check(modelFiles.size == 3) {
            "Expected three verified models in DAG_V2_BENCHMARK_CACHE"
        }
        check(corpusFiles.size in 50..100) {
            "Expected an exported Android subset of 50 to 100 images"
        }
    }
}

android {
    namespace = "com.contentfilter.dag2.benchmark"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.contentfilter.dag2.benchmark.dev"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "04a-local"
    }

    buildTypes {
        debug {
            isDebuggable = false
        }
        release {
            isMinifyEnabled = false
        }
    }

    sourceSets["main"].assets.srcDir(generatedAssets)

    packaging {
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) { variant ->
        variant.enable = false
    }
}

tasks.named("preBuild").configure {
    dependsOn(prepareBenchmarkAssets)
}

dependencies {
    implementation("com.google.mediapipe:tasks-vision:0.10.21")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.19.2")
}
