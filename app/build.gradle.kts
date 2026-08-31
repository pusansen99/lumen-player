plugins {
    // AGP 9 has built-in Kotlin support, so no kotlin-android plugin is applied.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

/** Version comes from git: name = nearest tag (v0.4.0 -> 0.4.0), code = commit count. */
fun gitOutput(vararg args: String): String =
    providers.exec {
        commandLine("git", *args)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()

val gitVersionName: String =
    gitOutput("describe", "--tags", "--abbrev=0").removePrefix("v").ifBlank { "0.0.0" }
val gitVersionCode: Int =
    gitOutput("rev-list", "--count", "HEAD").toIntOrNull() ?: 1

android {
    namespace = "com.lumen.player"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lumen.player"
        minSdk = 36
        targetSdk = 37
        versionCode = gitVersionCode
        versionName = gitVersionName

        // GitHub repo the in-app updater queries for the latest release.
        buildConfigField("String", "UPDATE_REPO", "\"pusansen99/lumen-player\"")

        // minSdk 36 devices are all arm64; other ABIs are dead weight.
        ndk { abiFilters += "arm64-v8a" }
    }

    // Only present in CI, populated from the SIGNING_* secrets by the Release workflow.
    // Unset locally -> debug builds keep using the machine's ~/.android/debug.keystore.
    val ciKeystore = providers.environmentVariable("SIGNING_KEYSTORE_PATH").orNull
    if (ciKeystore != null) {
        signingConfigs {
            create("ci") {
                storeFile = file(ciKeystore)
                storePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("ci")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Sign with the CI key when present, else the local debug key (same cert,
            // so the in-app updater stays compatible with the release history).
            signingConfig = signingConfigs.findByName("ci")
                ?: signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/*.version",
                "/META-INF/*.kotlin_module",
                "/kotlin/**",
                "DebugProbesKt.bin",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
        disable += setOf(
            // media3 @UnstableApi is opted into via the Kotlin compiler flag; Lint can't see that.
            "UnsafeOptInUsageError",
            // Dependency freshness is Dependabot's job, not a build gate.
            "GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion",
            "OldTargetApi",
            // The video "Open with" filters intentionally use several data tags.
            "IntentFilterUniqueDataAttributes",
            // allowBackup=true default is fine for this app.
            "DataExtractionRules",
        )
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }

    // Room's MigrationTestHelper loads the exported schema JSONs from the merged
    // assets folder. Robolectric unit tests read the debug variant's merged
    // assets, and the Room Gradle plugin only wires schemas into androidTest, so
    // add them to the debug asset set here (kept out of the release APK).
    sourceSets.getByName("debug").assets.srcDir("$projectDir/schemas")
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        optIn.add("androidx.media3.common.util.UnstableApi")
    }
}

// Robolectric's SDK 36 sandbox requires a Java 21 runtime (the app's minSdk is 36,
// so lower Robolectric SDKs can't parse the test manifest). Run unit tests on 21.
tasks.withType<Test>().configureEach {
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    )
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.smoothstreaming)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.ui)
    implementation(libs.media3.ui.compose)
    implementation(libs.media3.common)

    implementation(libs.okhttp)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.video)

    testImplementation(libs.junit)
    testImplementation(libs.org.json) // real org.json for unit tests (android.jar ships stubs)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.runner)
}
