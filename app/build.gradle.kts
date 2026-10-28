plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.hayu.app"
    compileSdk = 34

    defaultConfig {
        ndk { abiFilters += listOf("arm64-v8a") }
        applicationId = "com.hayu.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "1.0.2"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }

        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("com.google.android.gms:play-services-auth:21.0.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.1.0")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.3.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")

    // Coil (untuk load foto profil)
    implementation("io.coil-kt:coil:2.7.0")

    // OkHttp (untuk upload gambar ke imgbb)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // SVGA player (animasi frame, car, gift) - dari JitPack
    implementation("com.github.yyued:SVGAPlayer-Android:2.6.1")

    // Agora RTC SDK — voice room realtime
    implementation("io.agora.rtc:voice-rtc-basic:4.5.2")

    // Firebase Cloud Messaging — push notification
    implementation("com.google.firebase:firebase-messaging-ktx")
}
