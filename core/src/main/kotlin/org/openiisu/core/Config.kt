package org.openiisu.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Mirrors iiSU's `emuladores_default.jsonc` (see docs/reference). */
@Serializable
data class ConsoleConfig(
    val shortName: String,
    val longName: String = shortName,
    val releaseYear: String = "",
    val releaseDate: String = "",
    val manufacturer: String = "",
    val retroAchievementsId: String = "NA",
    val romExtensions: List<String> = emptyList(),
    val emulators: List<EmulatorConfig> = emptyList(),
) {
    fun accepts(fileName: String): Boolean = romExtensions.any { fileName.endsWith(it) }
}

@Serializable
data class EmulatorConfig(
    val id: String,
    val name: String,
    val routeType: RouteType = RouteType.path,
    val packages: List<String> = emptyList(),
    val commands: List<LaunchCommand> = emptyList(),
)

@Suppress("EnumEntryName")
enum class RouteType { path, uri }

@Serializable
data class LaunchCommand(val description: String = "", val command: String)

@Serializable
data class ConsoleCatalog(val consoles: List<ConsoleConfig>)

/** Entry of `supported_emulators_default.json`. */
@Serializable
data class SupportedEmulator(val name: String, val packages: List<String>)

object ConfigLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseCatalog(jsonc: String): ConsoleCatalog =
        json.decodeFromString(ConsoleCatalog.serializer(), stripComments(jsonc))

    fun parseSupportedEmulators(text: String): List<SupportedEmulator> =
        json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(SupportedEmulator.serializer()), text)

    /** Removes `//` line comments, ignoring `//` that appears inside string literals. */
    fun stripComments(src: String): String {
        val out = StringBuilder(src.length)
        var inString = false
        var i = 0
        while (i < src.length) {
            val c = src[i]
            when {
                inString -> {
                    out.append(c)
                    if (c == '\\' && i + 1 < src.length) out.append(src[++i])
                    else if (c == '"') inString = false
                }
                c == '"' -> { inString = true; out.append(c) }
                c == '/' && i + 1 < src.length && src[i + 1] == '/' -> {
                    while (i < src.length && src[i] != '\n') i++
                    continue
                }
                else -> out.append(c)
            }
            i++
        }
        return out.toString()
    }
}
