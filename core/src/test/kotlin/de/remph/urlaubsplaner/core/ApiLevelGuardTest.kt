// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import javax.tools.ToolProvider

/**
 * The permanent API level check (docs/plan.md): nothing in `:core` may call a java.* method that
 * Android at minSdk lacks. Gradle passes the SDK's `api-versions.xml`, the compiled classes and
 * the level in system properties (core/build.gradle.kts). Without an SDK (-PcoreOnly) the check
 * is skipped; in a normal build a missing SDK fails it, because then the check cannot be trusted.
 */
class ApiLevelGuardTest {
    private val maxApi = System.getProperty("urlaubsplaner.maxApi").toInt()
    private val apiVersions =
        System.getProperty("urlaubsplaner.apiVersions").takeIf { it.isNotBlank() }?.let(::File)
    private val optional = System.getProperty("urlaubsplaner.apiCheckOptional").toBoolean()

    private fun scanner(): ApiLevelScan {
        assumeTrue(apiVersions != null || !optional) {
            "No Android SDK (-PcoreOnly): the API level check was skipped"
        }
        checkNotNull(apiVersions) {
            "api-versions.xml not found; set ANDROID_HOME or sdk.dir in local.properties"
        }
        return ApiLevelScan(apiVersions, maxApi)
    }

    private fun classesIn(directory: File) =
        directory.walkTopDown().filter { it.extension == "class" }.toList()

    @Test
    fun `core calls nothing newer than minSdk`() {
        val main = System.getProperty("urlaubsplaner.mainClasses")
        val classes = main.split(File.pathSeparator).flatMap { classesIn(File(it)) }
        assertTrue(classes.isNotEmpty(), "no compiled classes in $main")

        assertEquals(emptyList<ApiLevelScan.Finding>(), scanner().scan(classes))
    }

    /** Compiles [body] as a method of a tiny class and scans that, to prove the scan can fail. */
    private fun scanSnippet(
        directory: File,
        body: String,
    ): List<ApiLevelScan.Finding> {
        val source = File(directory, "Probe.java")
        source.writeText("public class Probe { void probe() throws Exception { $body } }")
        val compiler =
            checkNotNull(ToolProvider.getSystemJavaCompiler()) { "tests need a JDK, not a JRE" }
        assertEquals(0, compiler.run(null, null, null, "-d", directory.path, source.path))
        return scanner().scan(classesIn(directory))
    }

    @Test
    fun `the scan fails on LocalDate datesUntil, which is API 34`(
        @TempDir directory: File,
    ) {
        val found =
            scanSnippet(
                directory,
                "java.time.LocalDate.now().datesUntil(java.time.LocalDate.now());",
            )

        assertTrue(
            found.any { it.owner == "java/time/LocalDate" && it.member.startsWith("datesUntil") },
            "$found",
        )
    }

    @Test
    fun `the scan fails on ByteArrayOutputStream toString with a Charset, which is API 33`(
        @TempDir directory: File,
    ) {
        val found =
            scanSnippet(
                directory,
                "new java.io.ByteArrayOutputStream()" +
                    ".toString(java.nio.charset.StandardCharsets.UTF_8);",
            )

        assertTrue(
            found.any {
                it.owner == "java/io/ByteArrayOutputStream" && it.member.startsWith("toString")
            },
            "$found",
        )
    }

    /** `invokeinterface` lines carry an argument count that the plain `invokevirtual` lines lack. */
    @Test
    fun `the scan fails on an interface method, Stream toList, which is API 34`(
        @TempDir directory: File,
    ) {
        val found =
            scanSnippet(
                directory,
                "java.util.Arrays.asList(1).stream().toList();",
            )

        assertTrue(
            found.any { it.owner == "java/util/stream/Stream" && it.member.startsWith("toList") },
            "$found",
        )
    }

    @Test
    fun `the scan passes ordinary old calls`(
        @TempDir directory: File,
    ) {
        val found =
            scanSnippet(
                directory,
                "java.time.LocalDate.now().plusDays(1).getDayOfWeek();" +
                    " new java.util.ArrayList<String>().size();" +
                    " new java.io.ByteArrayOutputStream().toString(\"UTF-8\");",
            )

        assertEquals(emptyList<ApiLevelScan.Finding>(), found)
    }
}
