plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.edgelite.panel"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.edgelite.panel"
        minSdk = 26
        targetSdk = 34
        versionCode = 14
        versionName = "1.2.3"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // Blok metadata dependensi terenkripsi milik Google tidak ikut dimasukkan ke APK.
    // Blok itu membuat build tidak bisa direproduksi dan tidak dibutuhkan F-Droid.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    // Pemeriksaan lint untuk build release dimatikan agar build F-Droid tidak gagal
    // karena temuan lint. Lint tetap bisa dijalankan manual dengan ./gradlew lint.
    lint {
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
