plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    // NOTE: Do NOT use applicationId "com.termux" unless you ship unpatched
    // Termux proot binaries. We keep our own ID and patch the binaries instead.
    // See docs/PROOT_PACKAGING.md and tools/patch_proot_prefix.py.
    namespace = "com.neurabuild.ide"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.neurabuild.ide"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            // ARM64 only: keeps APK small, matches minirootfs aarch64.
            abiFilters += listOf("arm64-v8a")
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
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // libproot.so is loaded as a native lib but executed as a binary.
        // Gradle must not strip/compress it in a way that breaks exec.
        jniLibs {
            keepDebugSymbols += listOf("**/libproot.so")
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material:material-icons-extended:1.6.8")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.8")
    debugImplementation("androidx.compose.ui:ui-tooling:1.6.8")

    // Networking (AI chat, OpenAI-compatible APIs)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Secure storage for API keys
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Chat history persistence
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Diff preview (line diffs before write)
    implementation("io.github.java-diff-utils:java-diff-utils:4.12")

    // Web preview reverse proxy (lightweight, no Ktor to save memory/APK size)
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
