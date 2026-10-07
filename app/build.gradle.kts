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
        versionCode = 22
        versionName = "1.3.1"
    }

    // Tanda tangan tetap untuk APK debug yang dibuat GitHub Actions, supaya update bisa dipasang
    // di atas versi lama tanpa uninstall. Kunci dibaca dari variabel lingkungan (GitHub Secrets).
    // Bila tidak ada (build lokal, fork, pull request, F-Droid), dipakai kunci debug bawaan.
    // Varian release sengaja tidak disentuh: F-Droid menandatanganinya dengan kuncinya sendiri.
    val keystorePath = System.getenv("EDGELITE_KEYSTORE_FILE")
    signingConfigs {
        if (keystorePath != null && file(keystorePath).exists()) {
            create("fixed") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("EDGELITE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("EDGELITE_KEY_ALIAS")
                keyPassword = System.getenv("EDGELITE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("fixed")?.let { signingConfig = it }
        }
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
