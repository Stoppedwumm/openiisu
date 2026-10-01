import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
}

compose.desktop {
    application {
        mainClass = "org.openiisu.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "openiisu"
            packageVersion = "1.0.0"
            macOS { bundleID = "org.openiisu.desktop" }
        }
    }
}

// Ship the shared reference configs as classpath resources.
sourceSets["main"].resources.srcDir("../docs/reference")
