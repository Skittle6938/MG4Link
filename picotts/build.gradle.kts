// Pico TTS: a standard Android speech engine built from the SVOX Pico sources (Apache 2.0).
// MG4Link bundles this APK and installs it on the car, where no speech engine ships, so it can
// speak to the phone's assistant (#71) and any other app can use it too.
plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.mg4link.picotts"
    compileSdk = 36
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.mg4link.picotts"
        minSdk = 28
        targetSdk = 36
        // Raise on every change: MG4Link reinstalls the bundled engine when its version is newer.
        versionCode = 1
        versionName = "1.0"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                cFlags("-O2", "-Wno-everything")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the build machine's debug key: MG4Link installs it itself and replaces
            // it when the signature differs, so no release key is needed.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        abortOnError = false
    }
}
