import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Load signing credentials from keystore.properties (kept out of version control).
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseSigning = keystoreProperties.getProperty("storeFile") != null

android {
    namespace = "com.ncalendar.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ncalendar.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    implementation("androidx.compose.runtime:runtime:1.11.0-beta02")
    implementation("androidx.compose.ui:ui:1.11.0-beta02")
    implementation("androidx.compose.ui:ui-graphics:1.11.0-beta02")
    implementation("androidx.compose.ui:ui-tooling-preview:1.11.0-beta02")
    implementation("androidx.compose.foundation:foundation:1.11.0-beta02")
    implementation("androidx.compose.material3:material3:1.5.0-alpha16")
    debugImplementation("androidx.compose.ui:ui-tooling:1.11.0-beta02")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // Subscribed .ics calendars: biweekly parses remote feeds; WorkManager refreshes
    // them in the background.
    implementation("net.sf.biweekly:biweekly:0.6.8")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
}

composeCompiler {
    // Mark java.time types as stable so event-list composables can skip
    // recomposition during scroll (see compose_stability.conf).
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose_stability.conf"))
}
