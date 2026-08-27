import java.util.Base64

plugins {
    id("com.android.application")
}

val brokerBaseUrl = providers.gradleProperty("brokerBaseUrl")
    .orElse("https://syeycayasyufedwoprea.supabase.co/functions/v1/glosh-remote-broker")
    .get()
val escapedBrokerBaseUrl = brokerBaseUrl
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\r", "")
    .replace("\n", "")

// DEV-ONLY signing identity. It is intentionally repository-stable so physical DEV builds from
// GitHub Actions can update one another. Never reuse it for production.
val stableDevStoreSource = rootProject.file("dev-signing/glosh-remote-dev.p12.b64")
val stableDevStoreFile = layout.buildDirectory
    .file("stable-dev-signing/glosh-remote-dev.p12")
    .get()
    .asFile
if (!stableDevStoreFile.exists()) {
    stableDevStoreFile.parentFile.mkdirs()
    stableDevStoreFile.writeBytes(
        Base64.getMimeDecoder().decode(stableDevStoreSource.readText()),
    )
}

android {
    namespace = "com.glosh.remote.spike"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.glosh.remote.spike"
        minSdk = 30
        targetSdk = 35
        versionCode = 22
        versionName = "0.1.0-dev22"

        buildConfigField("String", "BROKER_BASE_URL", "\"$escapedBrokerBaseUrl\"")

        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }

    signingConfigs {
        create("stableDev") {
            storeFile = stableDevStoreFile
            storePassword = "GloshRemoteDev2026!"
            keyAlias = "glosh-remote-dev"
            keyPassword = "GloshRemoteDev2026!"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("stableDev")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Production signing remains a separate release decision.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    implementation("com.github.MuntashirAkon:sun-security-android:1.1")
    implementation("org.conscrypt:conscrypt-android:2.5.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
}
