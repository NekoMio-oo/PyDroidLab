// Top-level build file.
plugins {
    id("com.android.application") version "8.5.2" apply false
    kotlin("android") version "2.0.21" apply false
    kotlin("plugin.compose") version "2.0.21" apply false
    // Chaquopy 12.0.0 only supports Python 3.8 + AGP ≤ 7.2, which is incompatible
    // with the Python 3.11 / AGP 8.5 stack used here.  13.0+ is published to
    // Maven Central (chaquo.com/maven only mirrors ≤ 12.0.0).
    // 15.0.1 → Python 3.8–3.12, AGP 7.0–8.5.  See README.md §Chaquopy.
    id("com.chaquo.python") version "15.0.1" apply false
}
