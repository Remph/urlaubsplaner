// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Finds calls in compiled `java.*` / `javax.*` / `android.*` code that an Android device with
 * [maxApi] does not have. `:core` is a plain JVM module, so neither the compiler nor lint's
 * NewApi look at it: `LocalDate.datesUntil` (API 34) and `ByteArrayOutputStream.toString(Charset)`
 * (API 33) crashed on an Android 11 emulator and passed every JVM test.
 *
 * The facts come from the SDK's `api-versions.xml`; the calls come from `javap -c -p`, which ships
 * with every JDK, so no extra library is needed.
 */
class ApiLevelScan(
    apiVersions: File,
    private val maxApi: Int,
) {
    private val classes: Map<String, ApiClass> = readClasses(apiVersions)

    /** One call or reference [owner].[member] that is missing or too new, with the reason. */
    data class Finding(
        val owner: String,
        val member: String,
        val reason: String,
    )

    /** Everything in [classFiles] that [maxApi] does not have, sorted; empty means clean. */
    fun scan(classFiles: List<File>): List<Finding> {
        val findings = sortedSetOf(compareBy<Finding>({ it.owner }, { it.member }))
        for (batch in classFiles.chunked(JAVAP_BATCH)) {
            val text = disassemble(batch)
            for (match in MEMBER_REFERENCE.findAll(text)) {
                val (owner, name, descriptor) = match.destructured
                if (isPlatform(owner)) check(owner, name, descriptor)?.let(findings::add)
            }
            for (match in CLASS_REFERENCE.findAll(text)) {
                val owner = match.groupValues[1]
                if (isPlatform(owner)) check(owner, null, "")?.let(findings::add)
            }
        }
        return findings.toList()
    }

    private fun isPlatform(owner: String) =
        owner.startsWith("java/") || owner.startsWith("javax/") || owner.startsWith("android/")

    private fun check(
        owner: String,
        name: String?,
        descriptor: String,
    ): Finding? {
        val type = classes[owner] ?: return Finding(owner, name.orEmpty(), "not in Android at all")
        if (type.since >
            maxApi
        ) {
            return Finding(owner, name.orEmpty(), "class since API ${type.since}")
        }
        if (name == null) return null
        val key = name + descriptor
        val since = sinceOf(owner, key) ?: sinceOf(owner, name, field = true)
        return when {
            since == null -> {
                Finding(owner, key, "not in Android at all")
            }

            since.first > maxApi -> {
                Finding(
                    owner,
                    key,
                    "since API ${since.first}, found in ${since.second}",
                )
            }

            else -> {
                null
            }
        }
    }

    /** The API level of a method or field and the class that declares it, looking up the supertypes. */
    private fun sinceOf(
        owner: String,
        member: String,
        field: Boolean = false,
    ): Pair<Double, String>? {
        val seen = mutableSetOf<String>()
        val pending = ArrayDeque(listOf(owner))
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            val type = classes[current]
            if (type == null || !seen.add(current)) continue
            val since = (if (field) type.fields else type.methods)[member]
            // A method is never older than its class.
            if (since != null) return maxOf(since, type.since) to current
            pending += type.supertypes
        }
        // Arrays and interfaces use Object's methods, which the list does not repeat per interface.
        return if (!field && owner != OBJECT) sinceOf(OBJECT, member) else null
    }

    private fun disassemble(classFiles: List<File>): String {
        val javap =
            File(System.getProperty("java.home"), "bin/javap").let {
                if (it.exists()) it else File("$it.exe")
            }
        val process =
            ProcessBuilder(listOf(javap.path, "-c", "-p") + classFiles.map { it.path })
                .redirectErrorStream(true)
                .start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "javap failed: ${output.take(500)}" }
        return output
    }

    private class ApiClass(
        val since: Double,
        val supertypes: List<String>,
        val methods: Map<String, Double>,
        val fields: Map<String, Double>,
    )

    private companion object {
        const val OBJECT = "java/lang/Object"
        const val JAVAP_BATCH = 50

        // "// Method java/time/LocalDate.datesUntil:(Ljava/time/LocalDate;)Ljava/util/stream/Stream;"
        // invokeinterface also prints its argument count: "invokeinterface #33,  1  // InterfaceMethod".
        val MEMBER_REFERENCE =
            Regex(
                """(?:invoke\w+|getstatic|getfield|putstatic|putfield)\s+#\d+(?:,\s*\d+)?\s+//\s+(?:Interface)?(?:Method|Field)\s+([\w/$]+)\.([\w<>$]+):(\S+)""",
            )

        // "new #7 // class java/util/Locale", also checkcast, instanceof, ldc.
        val CLASS_REFERENCE = Regex("""#\d+\s+//\s+class\s+([\w/$]+)""")

        fun readClasses(file: File): Map<String, ApiClass> {
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val result = HashMap<String, ApiClass>()
            val nodes = document.documentElement.getElementsByTagName("class")
            for (index in 0 until nodes.length) {
                val node = nodes.item(index) as Element
                val classSince = node.since()
                val supertypes = mutableListOf<String>()
                val methods = HashMap<String, Double>()
                val fields = HashMap<String, Double>()
                val children = node.childNodes
                for (child in 0 until children.length) {
                    val element = children.item(child) as? Element ?: continue
                    when (element.tagName) {
                        "extends", "implements" -> {
                            supertypes += element.getAttribute("name")
                        }

                        "method" -> {
                            methods[element.getAttribute("name")] =
                                element.since(classSince)
                        }

                        "field" -> {
                            fields[element.getAttribute("name")] = element.since(classSince)
                        }
                    }
                }
                result[node.getAttribute("name")] =
                    ApiClass(classSince, supertypes, methods, fields)
            }
            return result
        }

        /** "since" is a plain level like 34 or a decimal like 36.1 for newer minor releases. */
        fun Element.since(default: Double = 1.0): Double =
            getAttribute("since").toDoubleOrNull() ?: default
    }
}
