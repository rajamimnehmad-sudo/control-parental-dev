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

android {
    namespace = "com.glosh.remote.spike"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.glosh.remote.spike"
        minSdk = 30
        targetSdk = 35
        versionCode = 22
        versionName = "0.22.0-screen-awake"

        buildConfigField("String", "BROKER_BASE_URL", "\"$escapedBrokerBaseUrl\"")

        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
