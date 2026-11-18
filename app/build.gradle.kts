import java.util.Properties
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
val signing = Properties().apply {
    val path = rootProject.file(".signing/release.properties")
    if (path.isFile) path.inputStream().use { load(it) }
}
val debugSigning = Properties().apply {
    val path = rootProject.file(".local/debug.properties")
    if (path.isFile) path.inputStream().use { load(it) }
}
val robolectricSdk by configurations.creating
// Separate package so it can sit next to (and be told apart from) the full module.
val publicApplicationId = "io.github.howard_tse.colorosenhance.fluidcloud"
val legacyApplicationId = "dev.howard.colorosenhance"
android {
    namespace = "dev.howard.colorosenhance"
    compileSdk = 37
    buildToolsVersion = "37.0.0"
    defaultConfig {
        applicationId = publicApplicationId
        minSdk = 36
        targetSdk = 36
        versionCode = 71
        versionName = "1.31.1-fluidcloud"
        buildConfigField("String", "PUBLIC_APPLICATION_ID", "\"$publicApplicationId\"")
        buildConfigField("String", "LEGACY_APPLICATION_ID", "\"$legacyApplicationId\"")
        manifestPlaceholders["publicApplicationId"] = publicApplicationId
        manifestPlaceholders["moduleLabel"] = "流体云增强"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures { buildConfig = true; compose = true }
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file(".local/debug.p12")
            storePassword = debugSigning.getProperty("storePassword")
            keyAlias = debugSigning.getProperty("keyAlias")
            keyPassword = debugSigning.getProperty("keyPassword")
            storeType = "PKCS12"
        }
        create("release") {
            storeFile = rootProject.file(".signing/release.p12")
            storePassword = signing.getProperty("storePassword")
            keyAlias = signing.getProperty("keyAlias")
            keyPassword = signing.getProperty("keyPassword")
            storeType = "PKCS12"
        }
    }
    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    lint { abortOnError = true }
}
dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.4")
    implementation("io.github.kyant0:backdrop:2.0.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    robolectricSdk("org.robolectric:android-all-instrumented:16-robolectric-13921718-i7")
}
tasks.withType<Test>().configureEach {
    maxHeapSize = "2g"
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", robolectricSdk.singleFile.parent)
}
