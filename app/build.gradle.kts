plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// In GitHub Actions telt elke build op, zodat een nieuwe versie altijd over de oude heen installeert.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "nl.bis.screensaver"
    compileSdk = 35

    defaultConfig {
        applicationId = "nl.bis.screensaver"
        minSdk = 28
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
    }

    // Vaste sleutel, zodat updates zonder eerst te verwijderen geïnstalleerd kunnen worden.
    signingConfigs {
        create("bis") {
            storeFile = rootProject.file("signing/bis-screensaver.jks")
            storePassword = "bisscreensaver"
            keyAlias = "bis"
            keyPassword = "bisscreensaver"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("bis")
        }
        debug {
            signingConfig = signingConfigs.getByName("bis")
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
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("io.coil-kt:coil:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
