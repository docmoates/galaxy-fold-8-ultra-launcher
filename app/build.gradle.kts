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
// file is missing the release build uses the committed stable key below (then the debug keystore),
// so `assembleRelease` always produces an installable APK.
val foldosKeystorePath: String? = System.getenv("FOLDOS_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
val foldosKeystorePassword: String? = System.getenv("FOLDOS_KEYSTORE_PASSWORD")?.takeIf { it.isNotEmpty() }
val foldosKeyAlias: String? = System.getenv("FOLDOS_KEY_ALIAS")?.takeIf { it.isNotBlank() }
val foldosKeyPassword: String? = System.getenv("FOLDOS_KEY_PASSWORD")?.takeIf { it.isNotEmpty() }
val foldosHasReleaseKey: Boolean =
    foldosKeystorePath != null && foldosKeystorePassword != null && foldosKeyAlias != null &&
        File(foldosKeystorePath).exists()
// The committed stable signing key (PKCS12) used by debug AND release builds when no FOLDOS_* key is
// supplied. Create it once with: keytool -genkeypair -keystore app/foldos-signing.p12 -storetype pkcs12
// -alias foldos -keyalg RSA -keysize 2048 -validity 10000 -storepass foldos-launcher -keypass foldos-launcher
// -dname "CN=FoldOS Launcher". Until the file exists the build falls back to the debug keystore.
val foldosStableKeyFile: File = file("foldos-signing.p12")
if (!foldosStableKeyFile.exists()) {
    // Without a stable key every CI build gets its own debug certificate, so a new APK cannot update the
    // installed one in place (uninstall first). The build still succeeds; it only warns.
    logger.warn("foldos-signing.p12 missing: signing with the per-machine debug key")
}
val FOLDOS_STABLE_KEY_ALIAS = "foldos"
val FOLDOS_STABLE_KEY_PASSWORD = "foldos-launcher"
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
        versionCode = 173
        versionName = "1.7.3"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        // The stable project key, committed at app/foldos-signing.p12 so every build (CI debug, CI
        // release, a local build) is signed with the SAME certificate and a new APK installs over the
        // old one in place. Dev-grade key: it is public, so it is not a secret and not a store key.
        if (foldosStableKeyFile.exists()) {
            create("foldosStable") {
                storeFile = foldosStableKeyFile
                storeType = "pkcs12"
                storePassword = FOLDOS_STABLE_KEY_PASSWORD
                keyAlias = FOLDOS_STABLE_KEY_ALIAS
                keyPassword = FOLDOS_STABLE_KEY_PASSWORD
            }
        }
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
        debug {
            // Same key as release, so debug and release builds update each other in place.
            if (foldosStableKeyFile.exists()) {
                signingConfig = signingConfigs.getByName("foldosStable")
            }
        }
        release {
            isMinifyEnabled = foldosMinify
            isShrinkResources = foldosMinify
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Precedence: a key supplied through the FOLDOS_* environment variables (CI secrets, optional),
            // then the committed stable key, then the debug keystore as the last resort.
            signingConfig = when {
                foldosHasReleaseKey -> signingConfigs.getByName("foldosRelease")
                foldosStableKeyFile.exists() -> signingConfigs.getByName("foldosStable")
                else -> signingConfigs.getByName("debug")
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

    // Hub > Mail: encrypted storage for the Crosspoint server token.
    implementation(libs.androidx.security.crypto)

    // Performance: installs src/main/baseline-prof.txt (AGP packages it as assets/dexopt/baseline.prof)
    // on first launch so the hot startup paths are AOT-compiled without a profiling run.
    implementation(libs.androidx.profileinstaller)
}
