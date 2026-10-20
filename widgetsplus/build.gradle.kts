plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Widgets +: the home-screen widget companion to FoldOS (same repository, separate app).
// It signs with the same committed stable key as :app, so debug and release builds update each other in place.
val stableKeyFile: File = rootProject.file("app/foldos-signing.p12")

android {
    namespace = "com.foldos.widgetsplus"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.foldos.widgetsplus"
        minSdk = 34
        targetSdk = 35
        versionCode = 100
        versionName = "1.0.0"
    }

    signingConfigs {
        if (stableKeyFile.exists()) {
            create("stable") {
                storeFile = stableKeyFile
                storeType = "pkcs12"
                storePassword = "foldos-launcher"
                keyAlias = "foldos"
                keyPassword = "foldos-launcher"
            }
        }
    }

    buildTypes {
        debug {
            if (stableKeyFile.exists()) signingConfig = signingConfigs.getByName("stable")
        }
        release {
            isMinifyEnabled = false
            signingConfig = if (stableKeyFile.exists()) signingConfigs.getByName("stable") else signingConfigs.getByName("debug")
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

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
