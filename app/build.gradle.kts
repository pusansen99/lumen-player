plugins {
    // AGP 9 has built-in Kotlin support, so no kotlin-android plugin is applied.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
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
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lumen.player"
        minSdk = 36
        targetSdk = 36
        versionCode = gitVersionCode
        versionName = gitVersionName

        // GitHub repo the in-app updater queries for the latest release.
        buildConfigField("String", "UPDATE_REPO", "\"pusansen99/lumen-player\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        optIn.add("androidx.media3.common.util.UnstableApi")
    }
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
}
