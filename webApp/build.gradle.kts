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

// Test-only accepted input fixture/stream adaptation. No oracle or application resource is rewritten.
val webAcquisitionOracleSources = layout.buildDirectory.dir("generated/acquisitionOracle/kotlin")
val prepareWebAcquisitionOracle by tasks.registering {
    val fixture = rootProject.file("shared/src/commonTest/kotlin/com/furybook/dubl/acquisition/AcquisitionPriceFixtures.kt")
    val stream = rootProject.file("shared/src/desktopTest/kotlin/com/furybook/dubl/acquisition/AcquisitionPresentationSourceMatrixTest.kt")
    inputs.files(fixture, stream)
    outputs.dir(webAcquisitionOracleSources)
    doLast {
        val output = webAcquisitionOracleSources.get().asFile.resolve("com/furybook/dubl/acquisition")
        output.mkdirs()
        output.resolve(fixture.name).writeText(fixture.readText().replace("import com.furybook.core.json.*", "import com.furybook.web.testdata.*"))
        val original = stream.readText()
        val entry = "@Test fun all_rows_preserve_exact_direct_bulk_intent_and_unverified_source_presentation()"
        check(original.contains(entry)) { "Accepted stream changed; inspect this test adapter" }
        val source = original.replace("import com.furybook.core.json.*", "import com.furybook.web.testdata.*")
            .replace("import java.io.File", "")
            .replace("class AcquisitionPresentationSourceMatrixTest {", "class WebAcquisitionOracleStream(private val resourceTexts:Map<String,String>, private val expectedText:String, private val overlayText:String) {")
            .lineSequence().filterNot { it.contains("private val root=generateSequence") || it.contains("private val folder=File") }.joinToString("\n")
            .replace(entry, "fun forEachPresentation(observe: (String, AcquisitionUiModel) -> Unit)")
            .replace("FcpTextSource { javaClass.classLoader.getResource(it)?.readText() }", "FcpTextSource { resourceTexts[it] }")
            .replace("JsonReader(File(folder,\"author_expected_price_updates.json\").readText())", "JsonReader(overlayText)")
            .replace("val progress=System.getProperty(\"furybook.acquisition.sourceMatrix.progress\")?.let { File(it).bufferedWriter() }", "val progress:WebOracleProgress?=WebOracleProgress()")
            .replace("File(folder,\"expected_prices.json\").forEachLine", "forEachLine")
            .replace("assertEquals(AcquisitionUiPhase.Blocked,shown.phase,id);", "observe(id,shown);assertEquals(AcquisitionUiPhase.Blocked,shown.phase,id);")
            .replace("assertEquals(actual.cost,(current.price", "observe(id,current);assertEquals(actual.cost,(current.price")
            .replace("assertFalse(shown.canConfirm,id);assertTrue(shown.blocks", "observe(id,shown);assertFalse(shown.canConfirm,id);assertTrue(shown.blocks")
        check(!source.contains("javaClass") && !source.contains("System.getProperty") && !source.contains("File("))
        output.resolve("WebAcquisitionOracleStream.kt").writeText(source.replace("    private val scope=", "    private fun forEachLine(block:(String)->Unit) = expectedText.lineSequence().forEach(block)\n    private val scope=") + "\nprivate class WebOracleProgress { fun write(line:String) { println(\"T18C_WEB_ORIGINAL_INPUT_PROGRESS|\"+line.trim()) }; fun flush()=Unit; fun close()=Unit }\n")
    }
}
kotlin.sourceSets.getByName("jsTest").kotlin.srcDir(webAcquisitionOracleSources)
tasks.named("compileTestKotlinJs") { dependsOn(prepareWebAcquisitionOracle) }
