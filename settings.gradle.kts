pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
    // Versions live here so :core can build without resolving the Android Gradle Plugin.
    plugins {
        id("com.android.application") version "8.7.3"
        id("org.jetbrains.kotlin.android") version "2.0.21"
        id("org.jetbrains.kotlin.jvm") version "2.0.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
        id("org.jetbrains.compose") version "1.7.1"
    }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "openiisu"
include(":core")
// The Android app needs an SDK; skip it where none is configured (e.g. core-only CI).
if (System.getenv("ANDROID_HOME") != null || file("local.properties").exists()) include(":app")
// Desktop (macOS/Linux/Windows JVM) app; skip with -Pnodesktop.
if (!providers.gradleProperty("nodesktop").isPresent) include(":desktop")
