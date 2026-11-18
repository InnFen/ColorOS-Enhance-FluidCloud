import java.util.Properties

plugins {
    id("com.android.application")
}

// Optional fixed signing key so updates install over the previous build (see README).
val debugSigning = Properties().apply {
    val path = rootProject.file(".local/debug.properties")
    if (path.isFile) path.inputStream().use { load(it) }
}

android {
    namespace = "dev.pixellock.coloros"
    // libxposed 102 declares minCompileSdk 37.
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "dev.pixellock.coloros"
        minSdk = 34
        targetSdk = 36
        // CI sets these per run (see .github/workflows/build.yml) so every build has its own number.
        versionCode = System.getenv("PIXELLOCK_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("PIXELLOCK_VERSION_NAME") ?: "0.2.0-local"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        getByName("debug") {
            if (rootProject.file(".local/debug.p12").isFile) {
                storeFile = rootProject.file(".local/debug.p12")
                storePassword = debugSigning.getProperty("storePassword")
                keyAlias = debugSigning.getProperty("keyAlias")
                keyPassword = debugSigning.getProperty("keyPassword")
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    androidResources {
        // Keep the font uncompressed so SystemUI can open it straight from the APK.
        noCompress += "ttf"
    }

    lint { abortOnError = false }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")
}
