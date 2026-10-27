plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

android {
    namespace = "com.tgme.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tgme.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
        }
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore.jks")
            storePassword = (project.findProperty("RELEASE_STORE_PASSWORD") as String?) ?: "tgme1234"
            keyAlias = (project.findProperty("RELEASE_KEY_ALIAS") as String?) ?: "tgme"
            keyPassword = (project.findProperty("RELEASE_KEY_PASSWORD") as String?) ?: "tgme1234"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

// ⚠️ NOUVEAU : bloc chaquopy de haut niveau (spécifique KTS)
chaquopy {
    defaultConfig {
        version = "3.11"
        pip {
            install("telethon")
        }
    }
}

dependencies {
    // Chaquopy gère tout
}
