import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val buildNumber = providers.gradleProperty("buildNumber")
    .orElse(providers.environmentVariable("GITHUB_RUN_NUMBER"))
    .orElse("local")
    .get()

val commitCount = runCatching {
    providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
    }.standardOutput.asText.map { it.trim().toIntOrNull() ?: 1 }.get()
}.getOrDefault(1)

val gitSha = runCatching {
    providers.gradleProperty("gitSha")
        .orElse(providers.environmentVariable("GITHUB_SHA"))
        .map { it.take(7) }
        .orElse(
            providers.exec {
                commandLine("git", "rev-parse", "--short=7", "HEAD")
            }.standardOutput.asText.map { it.trim().ifEmpty { "dev" } },
        )
        .get()
}.getOrDefault("dev")

// Release variants never fall back to the public debug key: fail the build when a
// signed variant is requested without one. Checked from the requested task names so
// debug builds, tests and lint keep working with no secrets configured.
val hasReleaseSigning = providers.environmentVariable("LAPCRAFT_KEYSTORE").isPresent
val releaseRequested = gradle.startParameter.taskNames.any {
    it.contains("Stable", ignoreCase = true) || it.contains("Preview", ignoreCase = true)
}
if (releaseRequested && !hasReleaseSigning) {
    throw GradleException(
        "Release signing key not configured. Set LAPCRAFT_KEYSTORE, KEYSTORE_PASSWORD, " +
            "KEY_ALIAS and KEY_PASSWORD (see ci/verify-release.sh).",
    )
}

android {
    namespace = "io.github.jlai403.lapcraft"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.jlai403.lapcraft"
        minSdk = 29
        targetSdk = 36
        versionCode = 1000 + commitCount
        versionName = "0.2.$commitCount"
        buildConfigField("String", "BUILD_NUMBER", "\"$buildNumber\"")
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
        buildConfigField("String", "UPDATE_CHANNEL", "\"preview\"")
        buildConfigField("String", "POSTHOG_KEY", "\"${providers.environmentVariable("PUBLIC_POSTHOG_KEY").orNull.orEmpty()}\"")
        buildConfigField("String", "POSTHOG_HOST", "\"${providers.environmentVariable("PUBLIC_POSTHOG_HOST").orNull ?: "https://us.i.posthog.com"}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // A checked-in debug key keeps GitHub Actions APKs updatable over each other.
    // Release builds must use their own key.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("ci/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        val ksPath = providers.environmentVariable("LAPCRAFT_KEYSTORE").orNull
        if (ksPath != null) {
            create("release") {
                storeFile = file(ksPath)
                storePassword = providers.environmentVariable("KEYSTORE_PASSWORD").orNull
                    ?: error("KEYSTORE_PASSWORD is required when LAPCRAFT_KEYSTORE is set")
                keyAlias = providers.environmentVariable("KEY_ALIAS").orNull
                    ?: error("KEY_ALIAS is required when LAPCRAFT_KEYSTORE is set")
                keyPassword = providers.environmentVariable("KEY_PASSWORD").orNull
                    ?: error("KEY_PASSWORD is required when LAPCRAFT_KEYSTORE is set")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            // Dev build: production package id, preview channel (Developer card visible).
        }
        create("stable") {
            isDebuggable = false
            signingConfig = signingConfigs.findByName("release")
            buildConfigField("String", "UPDATE_CHANNEL", "\"stable\"")
        }
        create("preview") {
            isDebuggable = false
            applicationIdSuffix = ".preview"
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // The seed-laps.zip asset stores already-compressed HEVC clips; do not
        // deflate it a second time, and keep it streamable for fast extraction.
        noCompress += "zip"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Robolectric fetches android-all at test runtime via MavenArtifactFetcher, which
// bypasses Gradle's cache, retries and mirrors, so a single Maven Central blip
// fails every Robolectric test class. Declare the jar as a real dependency and
// point Robolectric at the resolved copy so it never leaves the machine.
val robolectricJars by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

val copyRobolectricJars by tasks.registering(Copy::class) {
    from(robolectricJars)
    into(layout.buildDirectory.dir("robolectric-jars"))
}

tasks.withType<Test>().configureEach {
    dependsOn(copyRobolectricJars)
    systemProperty(
        "robolectric.dependency.dir",
        layout.buildDirectory.dir("robolectric-jars").get().asFile.absolutePath,
    )
    maxParallelForks = 2
    maxHeapSize = "1g"
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2025.10.00"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-transformer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("com.posthog:posthog-android:3.77.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    robolectricJars("org.robolectric:android-all-instrumented:15-robolectric-13954326-i7")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test:core-ktx:1.7.0")
    testImplementation("org.robolectric:robolectric:4.17")

    androidTestImplementation(platform("androidx.compose:compose-bom:2025.10.00"))
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
