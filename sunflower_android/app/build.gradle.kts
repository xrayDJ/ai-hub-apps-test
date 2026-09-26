plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "app.sunflower"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.sunflower"
        minSdk = 31
        targetSdk = 36
        // CI's run number keeps every build's version increasing, so updates always install.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0) + 100
        versionName = "1.0.0"

        // The GenieX runtimes (llama.cpp, Hexagon NPU, OpenCL) ship arm64-v8a only.
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        // Non-secret key committed to the repo so every build installs over the last one.
        create("dev") {
            storeFile = rootProject.file("signing/sunflower-dev.jks")
            storePassword = "sunflower-dev"
            keyAlias = "sunflower-dev"
            keyPassword = "sunflower-dev"
        }
        // The owner's upload key, provided by CI from repository secrets when present.
        System.getenv("SUNFLOWER_UPLOAD_KEYSTORE_FILE")?.let { path ->
            create("upload") {
                storeFile = file(path)
                storePassword = System.getenv("SUNFLOWER_UPLOAD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SUNFLOWER_UPLOAD_KEY_ALIAS")
                keyPassword = System.getenv("SUNFLOWER_UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("dev")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("dev")
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
        // Translations and the GGUF-picking flow are intentional; see README.
        disable += setOf("MissingTranslation")
    }

    flavorDimensions += "distribution"
    productFlavors {
        // Sideloaded build: may request "All files access" to load models in place.
        create("full") {
            dimension = "distribution"
            buildConfigField("boolean", "ALL_FILES_ACCESS", "true")
        }
        // Google Play build: that permission is restricted there, so it is left out;
        // "Copy into app" covers phones where in-place loading fails.
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "ALL_FILES_ACCESS", "false")
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

    packaging {
        // GenieX loads its NPU/DSP libraries from the extracted native library
        // directory, so they must not stay compressed inside the APK.
        jniLibs.useLegacyPackaging = true
        // Sunflower runs GGUF through llama.cpp only. Qualcomm's QNN/QAIRT runtime
        // (for AI Hub's precompiled models) is ~180 MB of the SDK's 208 MB of
        // native code and is never used, so it is left out. llama.cpp's own NPU
        // path (ggml-hexagon + libggml-htp-v*.so) does not depend on it.
        jniLibs.excludes +=
            setOf(
                "**/libQnn*.so",
                "**/libhta_hexagon_runtime_*.so",
                "**/libPlatformValidatorShared.so",
                "**/libcalculator.so",
                "**/libCalculator_skel.so",
                "**/libgeniex_plugin_qairt.so",
                "**/libgeniex_core.so",
                "**/libgeniex-proc.so",
                "**/libgeniex-proc-vision.so",
                "**/libgeniex_vlm.so",
            )
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    jvmToolchain(17)
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.geniex.android)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.sqlcipher.android)

    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test.junit)
}
