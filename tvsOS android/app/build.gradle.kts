plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tvsos.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tvsos.launcher"
        minSdk = 21
        targetSdk = 28
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        debug {
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            // Assinado com a chave de debug de proposito: o objetivo e sideload
            // no Fire TV Stick, nao publicacao na loja.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    // Sem AndroidX de proposito: o app usa apenas APIs do framework,
    // o que reduz muito a chance de erro de build/sync.
}
