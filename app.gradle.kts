plugins {
    id("com.android.application")
}

android {
    namespace = "it.leonardo.antivirus"
    compileSdk = 34

    defaultConfig {
        applicationId = "it.leonardo.antivirus"
        minSdk = 29
        targetSdk = 34
        // Cresce a ogni costruzione automatica, così Android accetta sempre l'aggiornamento.
        versionCode = providers.gradleProperty("verCode").orNull?.toIntOrNull() ?: 1
        versionName = "3.0"
    }

    // Stessa firma a ogni costruzione: senza, Android rifiuta gli aggiornamenti ("firma in conflitto").
    signingConfigs {
        create("leonardo") {
            storeFile = file("leonardo.keystore")
            storePassword = "leonardo"
            keyAlias = "leonardo"
            keyPassword = "leonardo"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("leonardo")
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
