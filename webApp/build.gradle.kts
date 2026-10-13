plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    js {
        browser {
            // KGP 2.4.20 requires a named selector before reading config fragments.
            // The fail-closed fragment replaces this unused selector with the normal script.
            testTask {
                useKarma { useChrome() }
                inputs.property("furybookIdbPhase", project.providers.environmentVariable("FURYBOOK_IDB_REOPEN_PHASE").orElse("normal"))
            }
            commonWebpackConfig {
                outputFileName = "fury-book.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        jsTest.dependencies {
            implementation(kotlin("test"))
            implementation(npm("karma-script-launcher", "1.0.0"))
        }
        jsMain {
            // DUBL/FCP resources stay single-source with Android/Desktop.
            resources.srcDir(rootProject.file("shared/src/commonMain/resources"))
            dependencies {
                implementation(project(":shared"))
                implementation("org.jetbrains.compose.runtime:runtime:1.12.0")
                implementation("org.jetbrains.compose.foundation:foundation:1.12.0")
                implementation("org.jetbrains.compose.material3:material3:1.12.0-alpha03")
                implementation("org.jetbrains.compose.ui:ui:1.12.0")
                implementation("org.jetbrains.compose.components:components-resources:1.12.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.jetbrains.kotlinx:kotlinx-browser:0.5.0")
                implementation(npm("@supabase/supabase-js", "2.117.2"))
            }
        }
    }
}
