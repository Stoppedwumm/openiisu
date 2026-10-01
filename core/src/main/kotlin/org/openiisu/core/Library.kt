package org.openiisu.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/** User choice of emulator/command per console, mirrors default_emulator_options.json. */
@Serializable
data class ConsoleOption(
    val shortName: String,
    val emulator: String,
    val commandLabel: String = "",
    val routeType: RouteType = RouteType.path,
    val launchPreference: String = "Ask",
)

@Serializable
data class EmulatorOptions(val consoles: List<ConsoleOption> = emptyList()) {
    fun forConsole(shortName: String) = consoles.firstOrNull { it.shortName == shortName }
    fun with(opt: ConsoleOption) = EmulatorOptions(consoles.filterNot { it.shortName == opt.shortName } + opt)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
        fun parse(text: String) = json.decodeFromString(serializer(), text)
        fun load(file: File) = if (file.exists()) parse(file.readText()) else EmulatorOptions()
        fun save(file: File, o: EmulatorOptions) {
            file.parentFile?.mkdirs(); file.writeText(json.encodeToString(serializer(), o))
        }
    }
}

/** Scraped metadata cached per ROM id (mirrors ScraperMetadataDatabase). */
@Serializable
data class GameMetadata(
    val title: String,
    val overview: String = "",
    val releaseDate: String = "",
    val developers: List<String> = emptyList(),
    val publishers: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val boxartUrl: String? = null,
    val source: String = "",
)

class MetadataCache(private val file: File) {
    private val json = Json { prettyPrint = true }
    private val ser = MapSerializer(String.serializer(), GameMetadata.serializer())
    private val map: MutableMap<String, GameMetadata> =
        (if (file.exists()) json.decodeFromString(ser, file.readText()) else emptyMap()).toMutableMap()

    operator fun get(romId: String) = map[romId]
    operator fun set(romId: String, m: GameMetadata) { map[romId] = m; flush() }
    private fun flush() { file.parentFile?.mkdirs(); file.writeText(json.encodeToString(ser, map)) }
}
