plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// CI passes the run number so every published APK installs over the previous one.
val ciVersionCode = System.getenv("VERSION_CODE")?.toIntOrNull()

// Fixed release key. CI decodes it from a repo secret; locally, release builds stay unsigned.
val keystorePath: String? = System.getenv("SIGNING_KEYSTORE")

android {
    namespace = "io.github.yurmont.yapetracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.yurmont.yapetracker"
        minSdk = 26
        targetSdk = 35
        versionCode = ciVersionCode ?: 1
        versionName = "1.0.${ciVersionCode ?: 0}"
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // On-device OCR with the bundled Latin model: no download, no network.
    implementation("com.google.mlkit:text-recognition:16.0.1")

    testImplementation("junit:junit:4.13.2")
}
