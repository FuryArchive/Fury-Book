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
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "Fury Book Dev")
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
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            isDebuggable = false
            resValue("string", "app_name", "Fury Book Dev")
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

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
}
