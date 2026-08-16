plugins {
    id("com.android.application")
}

android {
    namespace = "br.com.rafael.aceitarapido"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.rafael.aceitarapido"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0-teste"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
