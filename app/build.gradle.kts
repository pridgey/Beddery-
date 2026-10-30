import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// CI passes these in; local builds fall back to sensible defaults.
val ciVersionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
val ciVersionName = System.getenv("VERSION_NAME") ?: "1.0.0"

// Each distribution has its own key. GitHub's signs the APKs people install directly (and must never
// change, or updates stop installing). Play's is only the upload key: Google re-signs for the store.
fun keystoreFrom(prefix: String) = System.getenv("${prefix}_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }
val githubKeystore = keystoreFrom("BEDDERY")
val playKeystore = keystoreFrom("PLAY")

android {
    namespace = "dev.pridgey.beddery"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.pridgey.beddery"
        minSdk = 28
        targetSdk = 36
        versionCode = ciVersionCode
        versionName = ciVersionName
        resValue("string", "dream_settings_component", "dev.pridgey.beddery/dev.pridgey.beddery.MainActivity")
    }

    // Without a keystore, release builds use the debug key so they still install.
    signingConfigs {
        if (githubKeystore != null) {
            create("github") {
                storeFile = githubKeystore
                storePassword = System.getenv("BEDDERY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("BEDDERY_KEY_ALIAS")
                keyPassword = System.getenv("BEDDERY_KEY_PASSWORD")
            }
        }
        if (playKeystore != null) {
            create("play") {
                storeFile = playKeystore
                storePassword = System.getenv("PLAY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PLAY_KEY_ALIAS")
                keyPassword = System.getenv("PLAY_KEY_PASSWORD")
            }
        }
    }

    // Two ways to ship the same app:
    //  - play: for Google Play, which delivers updates itself. No updater, no install permission.
    //  - github: the APK on GitHub Releases, which checks for and installs its own updates.
    flavorDimensions += "distribution"
    productFlavors {
        create("play") {
            dimension = "distribution"
            signingConfig = signingConfigs.findByName("play") ?: signingConfigs.getByName("debug")
        }
        create("github") {
            dimension = "distribution"
            // Where the app looks for updates. A read-only token is only needed while the repo is private.
            buildConfigField("String", "UPDATE_REPO", "\"${System.getenv("UPDATE_REPO") ?: "pridgey/Beddery"}\"")
            buildConfigField("String", "UPDATE_TOKEN", "\"${System.getenv("UPDATE_TOKEN") ?: ""}\"")
            signingConfig = signingConfigs.findByName("github") ?: signingConfigs.getByName("debug")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "dream_settings_component", "dev.pridgey.beddery.debug/dev.pridgey.beddery.MainActivity")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // No signingConfig here: a build type's would override the per-flavor keys above.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
        checkDependencies = false
        // ObsoleteSdkInt: adaptive icons must stay in mipmap-anydpi-v26 for aapt2.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi", "ObsoleteSdkInt")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.systemProperty("robolectric.graphicsMode", "NATIVE") }
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

base {
    archivesName.set("beddery-$ciVersionName")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
