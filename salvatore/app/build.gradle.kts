plugins {
    id("com.android.application")
}

android {
    namespace = "it.salvatore.ai"
    compileSdk = 36

    defaultConfig {
        applicationId = "it.salvatore.ai"
        minSdk = 29
        targetSdk = 34
        // Cresce a ogni costruzione automatica, così Android accetta sempre l'aggiornamento.
        versionCode = providers.gradleProperty("verCode").orNull?.toIntOrNull() ?: 1
        versionName = "1.5"
        // Il motore del modello locale esiste per questi processori; il tablet usa arm64.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    // Stessa firma a ogni costruzione: senza, Android rifiuta gli aggiornamenti ("firma in conflitto").
    signingConfigs {
        create("salvatore") {
            storeFile = file("salvatore.keystore")
            storePassword = "salvatore"
            keyAlias = "salvatore"
            keyPassword = "salvatore"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("salvatore")
            isMinifyEnabled = false
            isDebuggable = false
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Motore di Google per far girare un modello piccolo dentro il tablet (senza internet).
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.17.1")
}
