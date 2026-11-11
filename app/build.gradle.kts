import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

// The release key stays out of git and out of the cloud. It comes from environment variables or
// from an untracked keystore.properties in the repo root (both listed in docs/release.md). With
// neither, the release build is simply unsigned: F-Droid signs its own build, and the cloud has
// no key.
val signingKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val releaseSigning: Map<String, String> =
    run {
        val file = rootProject.file("keystore.properties")
        val fromFile =
            if (file.exists()) {
                Properties().also { props -> file.inputStream().use { props.load(it) } }
            } else {
                Properties()
            }
        signingKeys
            .mapNotNull { key ->
                val env = "URLAUBSPLANER_" + key.replace(Regex("([A-Z])"), "_$1").uppercase()
                (System.getenv(env) ?: fromFile.getProperty(key))?.let { key to it }
            }.toMap()
    }
// Half a key is a mistake, not "unsigned": better to stop than to ship an APK nobody can update.
check(releaseSigning.isEmpty() || releaseSigning.keys.containsAll(signingKeys)) {
    "Release signing is incomplete, missing: ${signingKeys - releaseSigning.keys}. See docs/release.md."
}

android {
    namespace = "de.remph.urlaubsplaner"
    compileSdk = 37

    defaultConfig {
        // The application ID is permanent, do not change it.
        applicationId = "de.remph.urlaubsplaner"
        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()
        targetSdk = 37
        // versionCode = major * 10000 + minor * 100 + patch (0.10.0 is 1000, 1.0.0 is 10000). F-Droid
        // names the changelog after it: fastlane/metadata/android/*/changelogs/<versionCode>.txt.
        versionCode = 1100
        versionName = "0.11.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigning.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(releaseSigning.getValue("storeFile"))
                storePassword = releaseSigning.getValue("storePassword")
                keyAlias = releaseSigning.getValue("keyAlias")
                keyPassword = releaseSigning.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // A different application ID, so the debug build installs next to the release build
            // (own data, own widgets, own file provider). The permanent ID stays the release one.
            applicationIdSuffix = ".debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            // R8 stays off for 0.9.0: Room, Glance, WorkManager and kotlinx-serialization reach
            // classes by reflection, and there is no emulator in the cloud to test a shrunk APK.
            // docs/release.md has the numbers and what to test before turning it on.
            isMinifyEnabled = false
        }
    }

    // F-Droid does not want the signing-blob dependency metadata (it is encrypted to Google).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        // The school holiday file with its ODbL text and README (not in app/, shared with :core's
        // tests). Everything in that directory ends up in the APK, so the licence travels with it.
        getByName("main").assets.directories.add("../data/school-holidays")
        // The migration tests read the committed schemas as assets. Robolectric takes its assets
        // from the debug build, not from the test source set; the release build stays without them.
        getByName("debug").assets.directories.add("schemas")
        // The same schemas for the migration tests that run on a device or emulator, where the
        // test APK has its own assets.
        getByName("androidTest").assets.directories.add("schemas")
    }

    testOptions {
        // Robolectric needs the merged resources and manifest to start a fake Android.
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

room {
    // Committed, so every schema change is reviewed and the migration tests can open old versions.
    schemaDirectory("$projectDir/schemas")
}

// Robolectric downloads its Android image from Maven Central on first use. Where Central is
// not reachable directly (the cloud container), pass a mirror: -ProbolectricRepoUrl=<url>.
tasks.withType<Test>().configureEach {
    providers.gradleProperty("robolectricRepoUrl").orNull?.let {
        systemProperty("robolectric.dependency.repo.url", it)
        systemProperty("robolectric.dependency.repo.id", "mirror")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    // AppCompatActivity and AppCompatDelegate carry the in-app language (Android 11 and up).
    implementation(libs.androidx.appcompat)
    // A few standard icons (back arrow, navigation bar); the core set only, not the big one.
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    // The home screen widget (Apache-2.0, brings WorkManager and DataStore, no Play Services).
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Room and Robolectric run on JUnit 4; :core keeps JUnit Jupiter.
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.glance.appwidget.testing)
    // Compose tests on the JVM through Robolectric: the screens compose, show the right texts.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Instrumented tests (src/androidTest): they run on an emulator or device, not in the cloud.
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
