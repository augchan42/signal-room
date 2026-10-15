import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.Exec

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

tasks.withType<Test>().configureEach {
    workingDir(rootProject.projectDir)
}

val demoAssetsDir = layout.projectDirectory.dir("src/main/assets/demo")
val demoAssetReport = layout.buildDirectory.file("reports/demo-assets.json")
val validateDemoAssets by tasks.registering(Exec::class) {
    description = "Decode and validate every bundled demo media file before APK packaging"
    group = "verification"
    inputs.files(fileTree(demoAssetsDir))
    inputs.file(rootProject.file("tools/validate_demo_assets.py"))
    outputs.file(demoAssetReport)
    commandLine(
        "python3", rootProject.file("tools/validate_demo_assets.py").absolutePath,
        "--root", demoAssetsDir.asFile.absolutePath,
        "--report", demoAssetReport.get().asFile.absolutePath,
    )
}

tasks.matching { it.name == "mergeDebugAssets" || it.name == "mergeReleaseAssets" }.configureEach {
    dependsOn(validateDemoAssets)
}

android {
    namespace = "dev.digitalrain.synthwave"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.digitalrain.synthwave"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Spike (throwaway): the Godot library ships four ABIs; the test phone needs one.
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions { unitTests.isIncludeAndroidResources = false }
    sourceSets.getByName("test").resources.srcDir("src/main/assets")
    // Spike (throwaway): the exported Godot project, unpacked from spikes/realtime-3d/build/spike3d.apk.
    sourceSets.getByName("main").assets.srcDir("../spikes/realtime-3d/build/godot-assets")
    // Godot keeps its imported files in the hidden .godot directory, which aapt skips by default.
    androidResources {
        ignoreAssetsPattern = "!.svn:!.git:!.gitignore:!.ds_store:!*.scc:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~"
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation("org.godotengine:godot:4.7.2.stable")
    implementation("androidx.fragment:fragment-ktx:1.8.5")

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
