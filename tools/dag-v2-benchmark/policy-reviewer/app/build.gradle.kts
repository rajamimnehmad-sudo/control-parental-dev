plugins {
    id("com.android.application")
}

val benchmarkCache =
    providers.environmentVariable("DAG_V2_BENCHMARK_CACHE").orElse(
        providers.systemProperty("user.home").map { "$it/.cache/dag-v2-benchmark" },
    )
val fixtureMode =
    providers.environmentVariable("DAG_V2_REVIEWER_FIXTURE")
        .map { it == "1" || it.equals("true", ignoreCase = true) }
        .orElse(false)
val generatedAssets = layout.buildDirectory.dir("generated/reviewerAssets")
val policyTool = rootProject.projectDir.parentFile.resolve("dag_v2_policy_eval.py")

val prepareReviewerAssets by tasks.registering(Exec::class) {
    val arguments =
        mutableListOf(
            "python3",
            policyTool.absolutePath,
            "--cache",
            benchmarkCache.get(),
            "prepare-reviewer-assets",
            "--output",
            generatedAssets.get().asFile.absolutePath,
        )
    if (fixtureMode.get()) {
        arguments += "--fixture"
    }
    commandLine(arguments)
    inputs.property("fixtureMode", fixtureMode)
    inputs.property("benchmarkCache", benchmarkCache)
    inputs.file(policyTool)
    if (!fixtureMode.get()) {
        inputs.files(
            rootProject.projectDir.parentFile.resolve("evidence/04a/corpus.lock.jsonl"),
            rootProject.projectDir.parentFile.resolve("evidence/04b/review-order.lock.jsonl"),
            rootProject.projectDir.parentFile.resolve("evidence/04b/plan.lock.json"),
        )
    }
    outputs.dir(generatedAssets)
    outputs.upToDateWhen { false }
}

android {
    namespace = "com.contentfilter.dag2.policyreviewer"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.contentfilter.dag2.policyreviewer.dev"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "04b-local"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isMinifyEnabled = false
        }
    }

    sourceSets["main"].assets.srcDir(generatedAssets)

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) { variant ->
        variant.enable = false
    }
}

tasks.named("preBuild").configure {
    dependsOn(prepareReviewerAssets)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
