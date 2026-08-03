plugins {
    id("com.android.application")
}

android {
    namespace = "com.contentfilter.gloshia.ortharness"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.contentfilter.gloshia.ortharness"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1-lab"
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.27.0")
}
