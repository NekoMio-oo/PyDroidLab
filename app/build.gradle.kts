plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

android {
    namespace = "com.pydroidlab.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pydroidlab.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            // Support modern 64-bit ARM devices and x86_64 Android emulators
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

chaquopy {
    defaultConfig {
        // Python 3.10 provides full pre-compiled wheel support in Chaquopy's repository
        // (including numpy, pandas, requests, etc.) and includes pip for runtime package installation.
        val pyVer = project.findProperty("pydroidlab.pythonVersion")?.toString() ?: "3.10"
        version = pyVer

        pip {
            // Core runtime & HTTP dependencies pre-installed so pip and requests work out of the box
            install("pip")
            install("wheel")
            install("six")
            install("certifi")
            install("charset-normalizer")
            install("idna")
            install("urllib3")
            install("requests")

            // Controlled by `pydroidlab.preinstallScientific` in gradle.properties (default: true)
            val preinstallScientific = project.findProperty("pydroidlab.preinstallScientific")
                ?.toString()
                ?.toBoolean() ?: true
            if (preinstallScientific) {
                install("numpy")
                install("pandas")
            }
        }

        pyc {
            src = false
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // Internal VT100/ANSI interactive terminal-emulator module
    implementation(project(":terminal-emulator"))

    // AndroidX Core & Lifecycle
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Jetpack Compose + Material 3
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Rosemoe Sora Editor (Syntax highlighting, line numbers, auto-indent, bracket matching, find/replace)
    val soraBom = platform("io.github.Rosemoe.sora-editor:bom:0.23.4")
    implementation(soraBom)
    implementation("io.github.Rosemoe.sora-editor:editor")
    implementation("io.github.Rosemoe.sora-editor:language-textmate")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Unit & Instrumentation Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
