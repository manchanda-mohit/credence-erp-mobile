// Top-level build file — plugin versions are declared here (with
// apply false) and actually applied per-module in app/build.gradle.kts.
// This "plugins block, not classpath" style is the modern Android
// Gradle Plugin convention and is what current Android Studio project
// wizards generate.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
}
