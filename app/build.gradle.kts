plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// ---- Release signing and shrinking (owned by the release step) ---------------------------------
// The release keystore comes from environment variables so no secret is ever committed. CI decodes
// the FOLDOS_KEYSTORE_BASE64 secret to a file and exports these four; when any is absent or the
// file is missing the release build falls back to the debug keystore, so `assembleRelease` always
// produces an installable (but debug-signed) APK.
val foldosKeystorePath: String? = System.getenv("FOLDOS_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
val foldosKeystorePassword: String? = System.getenv("FOLDOS_KEYSTORE_PASSWORD")?.takeIf { it.isNotEmpty() }
val foldosKeyAlias: String? = System.getenv("FOLDOS_KEY_ALIAS")?.takeIf { it.isNotBlank() }
val foldosKeyPassword: String? = System.getenv("FOLDOS_KEY_PASSWORD")?.takeIf { it.isNotEmpty() }
val foldosHasReleaseKey: Boolean =
    foldosKeystorePath != null && foldosKeystorePassword != null && foldosKeyAlias != null &&
        File(foldosKeystorePath).exists()
// R8 shrinking of the release build; the keep rules live in proguard-rules.pro (Performance step).
// `./gradlew assembleRelease -Pfoldos.minify=false` builds an unshrunk release to bisect R8 problems.
val foldosMinify: Boolean = (project.findProperty("foldos.minify") as String?)?.toBoolean() ?: true

android {
    namespace = "com.foldos.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.foldos.launcher"
        minSdk = 34
        targetSdk = 35
        versionCode = 100
        versionName = "1.0.0"
        vectorDrawables {
            useSupportLibrary = true
        }
        // AppAuth's RedirectUriReceiverActivity (declared in the library manifest) catches the OAuth
        // redirect "com.foldos.launcher:/oauth2redirect" used by mail.MailAuthActivity.
        manifestPlaceholders["appAuthRedirectScheme"] = "com.foldos.launcher"
    }

    signingConfigs {
        if (foldosHasReleaseKey) {
            create("foldosRelease") {
                storeFile = File(foldosKeystorePath!!)
                storePassword = foldosKeystorePassword
                keyAlias = foldosKeyAlias
                keyPassword = foldosKeyPassword ?: foldosKeystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = foldosMinify
            isShrinkResources = foldosMinify
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (foldosHasReleaseKey) {
                signingConfigs.getByName("foldosRelease")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    // CI must always produce a release APK: lint's fatal-error vital check on assembleRelease would
    // otherwise block it. Run `./gradlew lint` separately when auditing.
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.window)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Hub > Mail: Gmail API sign-in (AppAuth) and encrypted token storage.
    implementation(libs.openid.appauth)
    implementation(libs.androidx.security.crypto)

    // Performance: installs src/main/baseline-prof.txt (AGP packages it as assets/dexopt/baseline.prof)
    // on first launch so the hot startup paths are AOT-compiled without a profiling run.
    implementation(libs.androidx.profileinstaller)
}
