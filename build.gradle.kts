plugins {
    id("com.android.application") version "9.3.0" apply false
    id("com.android.kotlin.multiplatform.library") version "9.3.0" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.4.20" apply false
    id("org.jetbrains.kotlin.jvm") version "2.4.20" apply false
    id("org.jetbrains.compose") version "1.12.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}

// Kotlin/JS build: use the system Node.js instead of downloading a private copy.
// This keeps dependencyResolutionManagement strict and works in GitHub Actions once Node is set up.
allprojects {
    plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin> {
        extensions.getByType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec>().download = false
    }
}
