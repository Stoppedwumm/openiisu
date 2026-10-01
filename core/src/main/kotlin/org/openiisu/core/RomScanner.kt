package org.openiisu.core

import java.io.File

data class Rom(
    val consoleShortName: String,
    val path: String,
    val fileName: String,
    val sizeBytes: Long,
) {
    /** Display name: file name without extension and without trailing "(Region)"/"[tag]" groups. */
    val title: String get() = cleanTitle(fileName)

    val id: String get() = "$consoleShortName/$fileName"

    companion object {
        private val tags = Regex("""\s*[(\[][^)\]]*[)\]]""")
        fun cleanTitle(fileName: String): String =
            fileName.substringBeforeLast('.', fileName).replace(tags, "").trim().ifEmpty { fileName }
    }
}

/**
 * Scans `<root>/<shortName>/` for every console in the catalog (iiSU uses the console shortName as folder name).
 * Extension matching is case-sensitive, as in the reference config.
 */
class RomScanner(private val catalog: ConsoleCatalog) {
    fun scanConsole(root: File, console: ConsoleConfig): List<Rom> {
        val dir = File(root, console.shortName)
        if (!dir.isDirectory) return emptyList()
        return dir.walkTopDown()
            .filter { it.isFile && !it.name.startsWith(".") && console.accepts(it.name) }
            .map { Rom(console.shortName, it.path, it.name, it.length()) }
            .sortedBy { it.title.lowercase() }
            .toList()
    }

    fun scan(root: File): Map<ConsoleConfig, List<Rom>> =
        catalog.consoles.associateWith { scanConsole(root, it) }.filterValues { it.isNotEmpty() }
}
