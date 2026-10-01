package org.openiisu.core

import kotlinx.serialization.json.*
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

fun interface HttpGet {
    fun get(url: String, headers: Map<String, String>): String

    companion object {
        val Default = HttpGet { url, headers ->
            val req = HttpRequest.newBuilder(URI(url)).also { b -> headers.forEach { (k, v) -> b.header(k, v) } }.GET().build()
            val res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString())
            check(res.statusCode() in 200..299) { "HTTP ${res.statusCode()} for $url" }
            res.body()
        }
    }
}

private fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8)

/** TheGamesDB v1.1 – requires a user API key. Endpoints as seen in iiSU. */
class TheGamesDbClient(private val apiKey: String, private val http: HttpGet = HttpGet.Default) {
    fun search(title: String, platformId: Int? = null): List<GameMetadata> {
        val url = "https://api.thegamesdb.net/v1.1/Games/ByGameName?apikey=${enc(apiKey)}&name=${enc(title)}" +
            "&fields=overview,genres,developers,publishers" + (platformId?.let { "&filter[platform]=$it" } ?: "") +
            "&include=boxart"
        return parseSearch(http.get(url, emptyMap()))
    }

    companion object {
        private const val ART_FALLBACK = "https://cdn.thegamesdb.net/images/original/"

        fun parseSearch(body: String): List<GameMetadata> {
            val root = Json.parseToJsonElement(body).jsonObject
            val games = root["data"]?.jsonObject?.get("games")?.jsonArray ?: return emptyList()
            val inc = root["include"]?.jsonObject
            val base = inc?.get("boxart")?.jsonObject?.get("base_url")?.jsonObject?.get("original")?.jsonPrimitive?.content ?: ART_FALLBACK
            val boxart = inc?.get("boxart")?.jsonObject?.get("data")?.jsonObject.orEmpty()
            return games.map { g ->
                val o = g.jsonObject
                val id = o["id"]!!.jsonPrimitive.content
                val front = boxart[id]?.jsonArray?.map { it.jsonObject }?.firstOrNull { it["side"]?.jsonPrimitive?.content == "front" }
                fun ids(k: String) = o[k]?.takeIf { it is JsonArray }?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
                GameMetadata(
                    title = o["game_title"]!!.jsonPrimitive.content,
                    overview = o["overview"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    releaseDate = o["release_date"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    developers = ids("developers"), publishers = ids("publishers"), genres = ids("genres"),
                    boxartUrl = front?.get("filename")?.jsonPrimitive?.content?.let { base + it },
                    source = "thegamesdb",
                )
            }
        }
    }
}

/** SteamGridDB v2 – Bearer API key. */
class SteamGridDbClient(private val apiKey: String, private val http: HttpGet = HttpGet.Default) {
    private val auth get() = mapOf("Authorization" to "Bearer $apiKey")

    fun findGameId(title: String): Int? =
        parseFirstId(http.get("https://www.steamgriddb.com/api/v2/search/autocomplete/${enc(title)}", auth))

    fun grids(gameId: Int): List<String> = parseUrls(http.get("https://www.steamgriddb.com/api/v2/grids/game/$gameId", auth))
    fun heroes(gameId: Int): List<String> = parseUrls(http.get("https://www.steamgriddb.com/api/v2/heroes/game/$gameId", auth))
    fun logos(gameId: Int): List<String> = parseUrls(http.get("https://www.steamgriddb.com/api/v2/logos/game/$gameId", auth))
    fun icons(gameId: Int): List<String> = parseUrls(http.get("https://www.steamgriddb.com/api/v2/icons/game/$gameId", auth))

    companion object {
        fun parseFirstId(body: String): Int? =
            Json.parseToJsonElement(body).jsonObject["data"]?.jsonArray?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.int

        fun parseUrls(body: String): List<String> =
            Json.parseToJsonElement(body).jsonObject["data"]?.jsonArray?.mapNotNull { it.jsonObject["url"]?.jsonPrimitive?.contentOrNull }.orEmpty()
    }
}

/** Tries scrapers in order and caches the first hit. */
class MetadataService(private val cache: MetadataCache, private val tgdb: TheGamesDbClient?) {
    fun metadataFor(rom: Rom): GameMetadata? {
        cache[rom.id]?.let { return it }
        val hit = runCatching { tgdb?.search(rom.title)?.firstOrNull() }.getOrNull() ?: return null
        cache[rom.id] = hit
        return hit
    }
}
