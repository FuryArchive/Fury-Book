val desktopVersion = providers.environmentVariable("FURY_BOOK_VERSION").orNull
    ?: providers.environmentVariable("DUBL_VERSION").orNull
    ?: "0.5.0"

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))
    implementation("org.jetbrains.compose.material3:material3:1.12.0-alpha03")
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "com.furybook.desktop.MainKt"
        nativeDistributions {
            packageName = "FuryBook"
            packageVersion = desktopVersion
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Rpm,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
            )
            windows {
                iconFile.set(project.file("src/main/resources/fury-icon.ico"))
            }
            linux {
                iconFile.set(project.file("src/main/resources/fury-icon.png"))
            }
        }
    }
}
