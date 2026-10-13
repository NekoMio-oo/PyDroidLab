plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
    id("com.chaquo.python")
}

android {
    namespace = "com.pydroidlab.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pydroidlab.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "1.2.0-alpha01"

        vectorDrawables { useSupportLibrary = true }

        // Chaquopy needs these ABIs for its native libs.
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }
    }

    // ---- Chaquopy -----------------------------------------------------------
    // Embedded CPython interpreter.  `version` selects the CPython build.
    //
    // Only pure-Python third-party wheels are listed here so the APK stays
    // small (~40 MB); heavy scientific wheels (numpy/pandas/matplotlib/...)
    // are installed at runtime on demand via the in-app Package Manager,
    // which pulls Chaquopy's prebuilt Android wheels.
    //
    // NOTE: since Chaquopy 13 the plugin is published to Maven Central and the
    // configuration block moved to a top-level `chaquopy { }` extension; the
    // old `android.defaultConfig.python { }` DSL only exists in Groovy builds
    // of Chaquopy ≤ 12.  See README.md §2 for the full rationale.
    // ---- -------------------------------------------------------------------

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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            // Chaquopy bundles its own; avoid duplicate META-INF collisions.
            excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
        }
    }
}

chaquopy {
    defaultConfig {
        version = "3.11"
        pip {
            // Pure-python niceties preinstalled so the editor works offline
            // out of the box. Scientific stack is installed on demand.
            install("black")
            install("requests")
            options("--no-cache-dir")
        }
        // tutorials/ 与 snippets/ 需要以「真实文件」的形式存在，
        // 这样 pdl_resources.py 才能 listdir/open 它们。
        extractPackages("tutorials", "snippets")
    }
    sourceSets {
        // 默认的 Python 源码根是 src/main/python。bootstrap/ 里的
        // pdl_runner / pdl_host / pdl_pip ... 是以顶层模块名被 Kotlin 调用的
        // （Python.getInstance().getModule("pdl_runner")），因此必须把它
        // 也加成一个源码根，否则这些模块只能以 bootstrap.pdl_runner 访问。
        getByName("main") {
            srcDir("src/main/python/bootstrap")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // --- Compose (Material 3) ---
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // --- Code editor (Rosemoe sora-editor) ---
    implementation("io.github.Rosemoe.sora-editor:editor:0.23.6")

    // --- Terminal emulator (jackpal Android-Terminal-Emulator via JitPack) ---
    implementation("com.github.jackpal:Android-Terminal-Emulator:1.0.70")

    // --- Coroutines ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
