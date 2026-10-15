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
    testImplementation(kotlin("test-junit"))
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    implementation(project(":shared"))
    implementation("org.jetbrains.compose.material3:material3:1.12.0-alpha03")
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
}

compose.desktop {
    application {
        mainClass = "com.furybook.desktop.MainKt"
        nativeDistributions {
            packageName = "FuryBook"
            packageVersion = desktopVersion
            modules("java.net.http")
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

// Public offscreen Compose host; native Window/system acceptance is a separate gate.
tasks.withType<Test>().configureEach {
    systemProperty("java.awt.headless", "true")
    systemProperty("furybook.acquisition.ui.evidence", layout.buildDirectory.dir("reports/acquisition-ui").get().asFile.absolutePath)
    maxHeapSize = "2g"
    val testHome = layout.buildDirectory.dir("acquisition-test-home").get().asFile
    systemProperty("user.home", testHome.absolutePath)
    systemProperty("java.util.prefs.userRoot", testHome.resolve("preferences").absolutePath)
    environment("HOME", testHome.absolutePath)
    environment("XDG_CACHE_HOME", testHome.resolve(".cache").absolutePath)
    doFirst { testHome.resolve(".cache/fontconfig").mkdirs() }
}

// Accepted independent oracle inputs only, compiled in the test module against Shared's public API.
val desktopAcquisitionOracleSources = layout.buildDirectory.dir("generated/acquisitionOracle/kotlin")
val prepareDesktopAcquisitionOracle by tasks.registering {
    val fixture = rootProject.file("shared/src/commonTest/kotlin/com/furybook/dubl/acquisition/AcquisitionPriceFixtures.kt")
    val stream = rootProject.file("shared/src/desktopTest/kotlin/com/furybook/dubl/acquisition/AcquisitionPresentationSourceMatrixTest.kt")
    inputs.files(fixture, stream)
    outputs.dir(desktopAcquisitionOracleSources)
    doLast {
        val output=desktopAcquisitionOracleSources.get().asFile.resolve("com/furybook/dubl/acquisition")
        output.mkdirs()
        output.resolve(fixture.name).writeText(fixture.readText().replace("import com.furybook.core.json.*", "import com.furybook.desktop.*"))
        val entry="@Test fun all_rows_preserve_exact_direct_bulk_intent_and_unverified_source_presentation()"
        val original=stream.readText()
        check(original.contains(entry)) { "Accepted source stream changed; inspect the adapter" }
        output.resolve("DesktopAcquisitionOracleStream.kt").writeText(original
            .replace("import com.furybook.core.json.*", "import com.furybook.desktop.*")
            .replace("class AcquisitionPresentationSourceMatrixTest", "class DesktopAcquisitionOracleStream")
            .replace(entry, "fun forEachPresentation(observe: (String, AcquisitionUiModel) -> Unit)")
            .replace("= runMatrix(false)", "= runMatrix(false, observe)")
            .replace("@Test ", "")
            .replace("private fun runMatrix(performanceOnly: Boolean)", "private fun runMatrix(performanceOnly: Boolean, observe: (String, AcquisitionUiModel) -> Unit = { _, _ -> })")
            .replace("assertEquals(AcquisitionUiPhase.Blocked,shown.phase,id);", "observe(id,shown);assertEquals(AcquisitionUiPhase.Blocked,shown.phase,id);")
            .replace("assertEquals(actual.cost,(current.price", "observe(id,current);assertEquals(actual.cost,(current.price")
            .replace("assertFalse(shown.canConfirm,id);assertTrue(shown.blocks", "observe(id,shown);assertFalse(shown.canConfirm,id);assertTrue(shown.blocks"))
    }
}
kotlin.sourceSets.getByName("test").kotlin.srcDir(desktopAcquisitionOracleSources)
tasks.named("compileTestKotlin") { dependsOn(prepareDesktopAcquisitionOracle) }
tasks.withType<Test>().configureEach {
    systemProperty("furybook.acquisition.sourceMatrix.progress", layout.buildDirectory.file("reports/acquisition-desktop-matrix.progress").get().asFile.absolutePath)
}
