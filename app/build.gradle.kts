plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing comes from environment variables so CI can inject a keystore.
// When absent, the release build falls back to the debug key (installable, but
// only for local testing - use a real keystore for OTA updates).
val releaseKeystorePath = System.getenv("DJ2GO_KEYSTORE")
val releaseKeystoreFile = releaseKeystorePath?.let { rootProject.file(it) }
val hasReleaseKeystore = releaseKeystoreFile?.let { it.exists() && it.length() > 0L } == true

android {
    namespace = "com.dj2go"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dj2go"
        minSdk = 23
        targetSdk = 34
        versionCode = 7
        versionName = "0.7"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = System.getenv("DJ2GO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("DJ2GO_KEY_ALIAS")
                keyPassword = System.getenv("DJ2GO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
