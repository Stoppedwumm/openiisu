package org.openiisu.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class PlaytimeSession(val entryId: String, val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0)
}

/** Aggregate matching iiSU's PlaytimeEntry (id, totals, count, last played). */
data class PlaytimeEntry(
    val entryId: String,
    val totalPlaytimeMs: Long,
    val sessionCount: Int,
    val lastPlayedAtMs: Long,
)

interface PlaytimeStore {
    fun load(): List<PlaytimeSession>
    fun save(sessions: List<PlaytimeSession>)
}

class JsonFilePlaytimeStore(private val file: File) : PlaytimeStore {
    private val json = Json { prettyPrint = true }
    private val ser = ListSerializer(PlaytimeSession.serializer())
    override fun load() = if (file.exists()) json.decodeFromString(ser, file.readText()) else emptyList()
    override fun save(sessions: List<PlaytimeSession>) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(ser, sessions))
    }
}

/** Platform code calls [start] when a game is launched and [stop] when the user returns. */
class PlaytimeTracker(private val store: PlaytimeStore, private val clock: () -> Long = System::currentTimeMillis) {
    private val sessions = store.load().toMutableList()
    private var activeId: String? = null
    private var activeStart = 0L

    fun start(entryId: String) {
        if (activeId != null) stop()
        activeId = entryId
        activeStart = clock()
    }

    fun stop(): PlaytimeSession? {
        val id = activeId ?: return null
        activeId = null
        val s = PlaytimeSession(id, activeStart, clock())
        sessions += s
        store.save(sessions)
        return s
    }

    fun entries(): List<PlaytimeEntry> = sessions.groupBy { it.entryId }.map { (id, l) ->
        PlaytimeEntry(id, l.sumOf { it.durationMs }, l.size, l.maxOf { it.endMs })
    }.sortedByDescending { it.lastPlayedAtMs }

    fun entry(id: String) = entries().firstOrNull { it.entryId == id }
}
