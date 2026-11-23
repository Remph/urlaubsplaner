// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Bytecode level 17 so any JDK from 17 up builds it; the cloud container uses JDK 21.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // A new warning stops the build, so they do not pile up (tech audit 2026-11-21, finding 06).
        allWarningsAsErrors.set(true)
    }
}

// The tests read the real school holiday file; the app bundles the same directory as assets.
sourceSets {
    test {
        resources.srcDir(rootProject.file("data/school-holidays"))
    }
}

dependencies {
    // Only the JSON tree API for the backup file; no compiler plugin, so the format is written
    // out by hand and every bad value gets its own message.
    implementation(libs.kotlinx.serialization.json)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// The API level check (ApiLevelGuardTest) needs the SDK's list of which API came with which
// Android version. It finds it like the Android plugin does: environment variable first, then
// sdk.dir in local.properties. With -PcoreOnly there is no SDK, and the check is skipped.
val sdkDirInLocalProperties: String? =
    rootProject.file("local.properties").takeIf { it.exists() }?.let { file ->
        val props = Properties()
        file.inputStream().use { props.load(it) }
        props.getProperty("sdk.dir")
    }
val androidSdk: File? =
    listOfNotNull(
        System.getenv("ANDROID_HOME"),
        System.getenv("ANDROID_SDK_ROOT"),
        sdkDirInLocalProperties,
    ).map { File(it) }
        .firstOrNull { it.isDirectory }

// Any platform will do, newer ones list everything older too; the biggest file is the newest.
val apiVersionsFile: File? =
    androidSdk
        ?.resolve("platforms")
        ?.listFiles()
        ?.map { it.resolve("data/api-versions.xml") }
        ?.filter { it.isFile }
        ?.maxByOrNull { it.length() }

tasks.test {
    useJUnitPlatform()
    systemProperty("urlaubsplaner.maxApi", libs.versions.minSdk.get())
    systemProperty("urlaubsplaner.apiVersions", apiVersionsFile?.path.orEmpty())
    systemProperty("urlaubsplaner.apiCheckOptional", providers.gradleProperty("coreOnly").isPresent)
    systemProperty(
        "urlaubsplaner.mainClasses",
        sourceSets.main
            .get()
            .output.classesDirs.asPath,
    )
    // The list is read from the SDK, not declared by the task, so a changed SDK reruns the check.
    inputs.files(listOfNotNull(apiVersionsFile)).withPropertyName("apiVersions")
}
