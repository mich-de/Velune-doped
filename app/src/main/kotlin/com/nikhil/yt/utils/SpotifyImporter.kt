package com.nikhil.yt.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.regex.Pattern

object SpotifyImporter {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val userAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    data class SpotifyTrack(val title: String, val artist: String, val album: String?)
    data class SpotifyPlaylist(val name: String, val tracks: List<SpotifyTrack>)

    fun extractPlaylistId(url: String): String? {
        val pattern = Pattern.compile("playlist/([a-zA-Z0-9]{22})")
        val matcher = pattern.matcher(url)
        return if (matcher.find()) {
            matcher.group(1)
        } else null
    }

    suspend fun fetchPlaylist(url: String): Result<SpotifyPlaylist> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val playlistId = extractPlaylistId(url)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid Spotify playlist URL"))

        val fetchUrl = "https://open.spotify.com/embed/playlist/$playlistId"
        val request = Request.Builder()
            .url(fetchUrl)
            .header("User-Agent", userAgent)
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Failed to load playlist page: ${response.code}"))
                }
                val html = response.body?.string() ?: ""
                if (html.isBlank()) {
                    return@withContext Result.failure(Exception("Empty response from Spotify"))
                }

                // 1. Parse __NEXT_DATA__ or initial-state JSON
                val regexNextData = Regex("""<script\s+id="__NEXT_DATA__"\s+type="application/json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
                val regexInitialState = Regex("""<script\s+id="initial-state"\s+type="application/json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)

                val jsonString = regexNextData.find(html)?.groupValues?.get(1)
                    ?: regexInitialState.find(html)?.groupValues?.get(1)
                    ?: return@withContext Result.failure(Exception("Could not find Spotify playlist data in HTML. Make sure the playlist is public."))

                val jsonElement = Json.parseToJsonElement(jsonString)
                
                // 2. Parse playlist name
                val nameFromJson = findPlaylistName(jsonElement)
                val ogTitleRegex = Regex("""<meta\s+property="og:title"\s+content="([^"]+)"""", RegexOption.IGNORE_CASE)
                val titleRegex = Regex("""<title>([^<]+)</title>""", RegexOption.IGNORE_CASE)
                val rawName = nameFromJson
                    ?: ogTitleRegex.find(html)?.groupValues?.get(1)
                    ?: titleRegex.find(html)?.groupValues?.get(1)?.substringBefore(" | Spotify")?.substringBefore(" - playlist")
                    ?: "Imported Spotify Playlist"
                val playlistName = rawName.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")

                val tracksArray = findTracksArray(jsonElement)
                    ?: return@withContext Result.failure(Exception("Could not parse tracks array from page data."))

                val tracksList = mutableListOf<SpotifyTrack>()
                for (item in tracksArray) {
                    if (item is JsonObject) {
                        val parsed = parseTrackItem(item)
                        if (parsed != null) {
                            tracksList.add(parsed)
                        }
                    }
                }

                Result.success(SpotifyPlaylist(playlistName, tracksList))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun findPlaylistName(jsonElement: JsonElement): String? {
        if (jsonElement is JsonObject) {
            val type = jsonElement["type"]?.jsonPrimitive?.content
            if (type == "playlist") {
                val name = jsonElement["name"]?.jsonPrimitive?.content
                    ?: jsonElement["title"]?.jsonPrimitive?.content
                if (name != null) return name
            }
            for (value in jsonElement.values) {
                val found = findPlaylistName(value)
                if (found != null) return found
            }
        } else if (jsonElement is JsonArray) {
            for (value in jsonElement) {
                val found = findPlaylistName(value)
                if (found != null) return found
            }
        }
        return null
    }

    private fun findTracksArray(jsonElement: JsonElement): JsonArray? {
        if (jsonElement is JsonObject) {
            if (jsonElement.containsKey("trackList") && jsonElement["trackList"] is JsonArray) {
                return jsonElement["trackList"] as JsonArray
            }
            if (jsonElement.containsKey("tracks") && jsonElement["tracks"] is JsonObject) {
                val tracksObj = jsonElement["tracks"] as JsonObject
                if (tracksObj.containsKey("items") && tracksObj["items"] is JsonArray) {
                    return tracksObj["items"] as JsonArray
                }
            }
            if (jsonElement.containsKey("items") && jsonElement["items"] is JsonArray) {
                val itemsArr = jsonElement["items"] as JsonArray
                val first = itemsArr.firstOrNull() as? JsonObject
                if (first != null && (first.containsKey("track") || first.containsKey("name") || first.containsKey("title"))) {
                    return itemsArr
                }
            }
            for (value in jsonElement.values) {
                val found = findTracksArray(value)
                if (found != null) return found
            }
        } else if (jsonElement is JsonArray) {
            for (value in jsonElement) {
                val found = findTracksArray(value)
                if (found != null) return found
            }
        }
        return null
    }

    private fun parseTrackItem(item: JsonObject): SpotifyTrack? {
        val track = item["track"] as? JsonObject ?: item
        val name = track["name"]?.jsonPrimitive?.content
            ?: track["title"]?.jsonPrimitive?.content
            ?: return null

        val artistsArr = track["artists"] as? JsonArray
        val artistName = if (artistsArr != null && artistsArr.isNotEmpty()) {
            val firstArtist = artistsArr[0] as? JsonObject
            firstArtist?.get("name")?.jsonPrimitive?.content ?: ""
        } else {
            track["artistName"]?.jsonPrimitive?.content
                ?: track["artist"]?.jsonPrimitive?.content
                ?: track["subtitle"]?.jsonPrimitive?.content
                ?: ""
        }

        val album = (track["album"] as? JsonObject)?.get("name")?.jsonPrimitive?.content
            ?: track["albumName"]?.jsonPrimitive?.content

        return SpotifyTrack(name, artistName, album)
    }
}
