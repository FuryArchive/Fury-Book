plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val appVersionName = providers.environmentVariable("FURY_BOOK_VERSION_NAME").orNull
    ?: providers.environmentVariable("DUBL_VERSION_NAME").orNull
    ?: "0.5"
val appVersionCode = providers.environmentVariable("FURY_BOOK_VERSION_CODE").orNull?.toIntOrNull()
    ?: providers.environmentVariable("DUBL_VERSION_CODE").orNull?.toIntOrNull()
    ?: 105000000
val releaseKeystorePath = providers.environmentVariable("FURY_BOOK_KEYSTORE_PATH").orNull
    ?: providers.environmentVariable("DUBL_KEYSTORE_PATH").orNull
val releaseKeystorePassword = providers.environmentVariable("FURY_BOOK_KEYSTORE_PASSWORD").orNull
    ?: providers.environmentVariable("DUBL_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("FURY_BOOK_KEY_ALIAS").orNull
    ?: providers.environmentVariable("DUBL_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("FURY_BOOK_KEY_PASSWORD").orNull
    ?: providers.environmentVariable("DUBL_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.furybook.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.dubl.character.android"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resValue("string", "app_name", "Fury Book")
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("ci/dubl-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".preview"
            resValue("string", "app_name", "Fury Book")
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("preview") {
            // Performance-testable CI build: release optimizations, but signed with
            // the repository's debug key and installed next to the stable app.
            initWith(getByName("release"))
            applicationIdSuffix = ".preview"
            isDebuggable = false
            resValue("string", "app_name", "Fury Book")
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            assets.srcDir(rootProject.file("shared/src/commonMain/resources"))
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation(project(":shared"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.4.20")
    androidTestImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.4.20")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

// Test-only projections and unchanged independent source expectations. No release/main inputs.
val androidAcquisitionOracleSources = layout.buildDirectory.dir("generated/acquisitionOracle/kotlin")
val androidAcquisitionOracleResources = layout.buildDirectory.dir("generated/acquisitionOracle/resources")
val prepareAndroidAcquisitionOracle by tasks.registering {
    val generatedSources = androidAcquisitionOracleSources.get().asFile
    val generatedResources = androidAcquisitionOracleResources.get().asFile
    val sharedFixture = rootProject.file("shared/src/commonTest/kotlin/com/furybook/dubl/acquisition/AcquisitionPriceFixtures.kt")
    val sharedStream = rootProject.file("shared/src/desktopTest/kotlin/com/furybook/dubl/acquisition/AcquisitionPresentationSourceMatrixTest.kt")
    val oracle = rootProject.file("rulesets/dubl-3.69/acquisition")
    inputs.files(sharedFixture, sharedStream, oracle.resolve("expected_prices.json"), oracle.resolve("author_expected_price_updates.json"))
    outputs.dirs(generatedSources, generatedResources)
    doLast {
        val output = generatedSources.resolve("com/furybook/dubl/acquisition")
        output.mkdirs()
        output.resolve(sharedFixture.name).writeText(sharedFixture.readText().replace("import com.furybook.core.json.*", "import com.furybook.android.acquisition.oracle.*"))
        val entry = "@Test fun all_rows_preserve_exact_direct_bulk_intent_and_unverified_source_presentation()"
        val original = sharedStream.readText()
        check(original.contains(entry)) { "Accepted oracle stream entry changed; inspect before updating the adapter" }
        val helper = original.replace("import com.furybook.core.json.*", "import com.furybook.android.acquisition.oracle.*").replace("class AcquisitionPresentationSourceMatrixTest", """class AndroidAcquisitionOracleStream(
                private val readText: (String) -> String? = { AndroidAcquisitionOracleStream::class.java.classLoader.getResource(it)?.readText() },
                private val readRows: ((String) -> Unit) -> Unit = { consume ->
                    checkNotNull(AndroidAcquisitionOracleStream::class.java.classLoader.getResource("acquisition-oracle/expected_prices.json"))
                        .openStream().bufferedReader().useLines { lines -> lines.forEach(consume) }
                })""")
            .replace(entry, "fun forEachPresentation(observe: (String, AcquisitionUiModel) -> Unit)")
            .replace("= runMatrix(false)", "= runMatrix(false, observe)")
            .replace("@Test ", "")
            .replace("private fun runMatrix(performanceOnly: Boolean)", "private fun runMatrix(performanceOnly: Boolean, observe: (String, AcquisitionUiModel) -> Unit = { _, _ -> })")
            .replace(Regex("(?m)^    private val (root|folder)=.*\\n"), "")
            .replace("javaClass.classLoader.getResource(it)?.readText()", "readText(it)")
            .replace("File(folder,\"author_expected_price_updates.json\").readText()", "checkNotNull(readText(\"acquisition-oracle/author_expected_price_updates.json\"))")
            .replace("File(folder,\"expected_prices.json\").forEachLine", "readRows")
            .replace("return@forEachLine", "return@readRows")
            .replace("assertEquals(AcquisitionUiPhase.Blocked,shown.phase,id);", "observe(id, shown);assertEquals(AcquisitionUiPhase.Blocked,shown.phase,id);")
            .replace("assertEquals(actual.cost,(current.price", "observe(id, current);assertEquals(actual.cost,(current.price")
            .replace("assertFalse(shown.canConfirm,id);assertTrue(shown.blocks", "observe(id, shown);assertFalse(shown.canConfirm,id);assertTrue(shown.blocks")
        output.resolve("AndroidAcquisitionOracleStream.kt").writeText(helper)
        val resources = generatedResources.resolve("acquisition-oracle")
        resources.mkdirs()
        listOf("expected_prices.json", "author_expected_price_updates.json").forEach { oracle.resolve(it).copyTo(resources.resolve(it), overwrite = true) }
    }
}
android.sourceSets.getByName("test").kotlin.srcDir(androidAcquisitionOracleSources.get().asFile)
android.sourceSets.getByName("test").kotlin.srcDir("src/acquisitionTest/kotlin")
android.sourceSets.getByName("test").resources.srcDir(androidAcquisitionOracleResources.get().asFile)
android.sourceSets.getByName("test").resources.srcDir(rootProject.file("shared/src/commonMain/resources"))
android.sourceSets.getByName("androidTest").kotlin.srcDir(androidAcquisitionOracleSources.get().asFile)
android.sourceSets.getByName("androidTest").kotlin.srcDir("src/acquisitionTest/kotlin")
android.sourceSets.getByName("androidTest").assets.srcDir(androidAcquisitionOracleResources.get().asFile)
tasks.matching { it.name in setOf("compileDebugUnitTestKotlin", "processDebugUnitTestJavaRes", "compileDebugAndroidTestKotlin", "mergeDebugAndroidTestAssets") }.configureEach {
    dependsOn(prepareAndroidAcquisitionOracle)
}
tasks.withType<Test>().configureEach {
    systemProperty("furybook.acquisition.sourceMatrix.progress", layout.buildDirectory.file("reports/acquisition-android-matrix.progress").get().asFile.absolutePath)
    maxHeapSize = "2g"
}
