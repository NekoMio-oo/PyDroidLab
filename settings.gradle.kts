pluginManagement {
    repositories {
        google()
        mavenCentral()
        // Chaquopy publishes its Gradle plugin & runtime to its own repo.
        maven { url = uri("https://chaquo.com/maven") }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // Chaquopy Python runtime + prebuilt binary wheels (numpy, pandas, ...).
        maven { url = uri("https://chaquo.com/maven") }
        // jackpal terminal-emulator is distributed via JitPack.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "PyDroidLab"
include(":app")
