plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Bei jedem Bau auf GitHub steigt die Versionsnummer automatisch,
// damit sich neue Versionen einfach über die alte installieren lassen.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "de.barkau.fotomail"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.barkau.fotomail"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    // Fester Signaturschlüssel: Updates lassen sich ohne Deinstallation einspielen.
    signingConfigs {
        create("fotomail") {
            storeFile = file("fotomail.keystore")
            storePassword = "fotomail-key"
            keyAlias = "fotomail"
            keyPassword = "fotomail-key"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fotomail")
        }
        debug {
            signingConfig = signingConfigs.getByName("fotomail")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.md",
                "META-INF/DEPENDENCIES"
            )
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.security:security-crypto:1.0.0")
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")

    // Wundfoto: Kamera direkt in der App
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
}
