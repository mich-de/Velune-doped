/*
 * Velune Project Original (2026)
 * Kòi Natsuko (github.com/koiverse)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.nikhil.yt.lrclib

import com.nikhil.yt.lrclib.models.MAX_DURATION_DIFFERENCE_SECONDS
import com.nikhil.yt.lrclib.models.Track
import com.nikhil.yt.lrclib.models.bestMatchingFor
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlin.math.abs

object LrcLib {
    private val client by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        isLenient = true
                        ignoreUnknownKeys = true
                    },
                )
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 10000
                socketTimeoutMillis = 15000
            }

            defaultRequest {
                url("https://lrclib.net")
            }

            expectSuccess = true
        }
    }

    private suspend fun queryLyrics(
        artist: String,
        title: String,
        album: String? = null,
    ) = client
        .get("/api/search") {
            parameter("track_name", title)
            parameter("artist_name", artist)
            if (album != null) parameter("album_name", album)
        }.body<List<Track>>()
        .filter { it.syncedLyrics != null }

    /** Version suffixes LrcLib titles rarely carry: "(1995 Digital Remaster)", "- Remastered 2011", "[Mono]". */
    private val VERSION_SUFFIX =
        Regex(
            """\s*(?:[(\[][^)\]]*\b(?:remaster(?:ed)?|remasterizzat[oa]|version|versione|mono|stereo|edit|deluxe|anniversary)\b[^)\]]*[)\]]|\s-\s[^-]*\b(?:remaster(?:ed)?|remasterizzat[oa]|version|versione|mono|stereo|edit)\b.*$)""",
            RegexOption.IGNORE_CASE,
        )

    /** [title] without version suffixes, or [title] itself when nothing is left. */
    internal fun cleanTitle(title: String): String = title.replace(VERSION_SUFFIX, "").trim().ifEmpty { title }

    suspend fun getLyrics(
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
    ) = runCatching {
        findSyncedLyrics(title, artist, duration, album)
            ?: cleanTitle(title).takeIf { it != title }?.let { findSyncedLyrics(it, artist, duration, album) }
            ?: throw IllegalStateException("Lyrics unavailable")
    }

    private suspend fun findSyncedLyrics(
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): String? {
        val tracks = queryLyrics(artist, title, album)
        val match = if (duration == -1) tracks.bestMatchingFor(duration, title, artist) else tracks.bestMatchingFor(duration)
        return match?.syncedLyrics?.let { LrcLib.Lyrics(it) }?.text
    }

    suspend fun getAllLyrics(
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
        callback: (String) -> Unit,
    ) {
        val tracks =
            queryLyrics(artist, title, album).ifEmpty {
                cleanTitle(title).takeIf { it != title }?.let { queryLyrics(artist, it, album) }.orEmpty()
            }
        var count = 0
        var plain = 0

        val sortedTracks = when {
            duration == -1 -> {
                tracks.sortedByDescending { track ->
                    var score = 0.0

                    if (track.syncedLyrics != null) score += 1.0

                    val titleSimilarity = calculateStringSimilarity(title, track.trackName)
                    val artistSimilarity = calculateStringSimilarity(artist, track.artistName)
                    score += (titleSimilarity + artistSimilarity) / 2.0
                    
                    score
                }
            }
            else -> {
                tracks.sortedBy { abs(it.duration.toInt() - duration) }
            }
        }

        sortedTracks.forEach { track ->
            currentCoroutineContext().ensureActive() // Corrected usage
            if (count <= 4) {
                if (track.syncedLyrics != null && duration == -1) {
                    count++
                    track.syncedLyrics.let(callback)
                } else {
                    if (track.syncedLyrics != null && abs(track.duration.toInt() - duration) <= MAX_DURATION_DIFFERENCE_SECONDS) {
                        count++
                        track.syncedLyrics.let(callback)
                    }
                    if (track.plainLyrics != null && abs(track.duration.toInt() - duration) <= 2 && plain == 0) {
                        count++
                        plain++
                        track.plainLyrics.let(callback)
                    }
                }
            }
        }
    }

    private fun calculateStringSimilarity(str1: String, str2: String): Double {
        val s1 = str1.trim().lowercase()
        val s2 = str2.trim().lowercase()
        
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0
        
        return when {
            s1.contains(s2) || s2.contains(s1) -> 0.8
            else -> {
                val maxLength = maxOf(s1.length, s2.length)
                val distance = levenshteinDistance(s1, s2)
                1.0 - (distance.toDouble() / maxLength)
            }
        }
    }

    private fun levenshteinDistance(str1: String, str2: String): Int {
        val len1 = str1.length
        val len2 = str2.length
        val matrix = Array(len1 + 1) { IntArray(len2 + 1) }
        
        for (i in 0..len1) matrix[i][0] = i
        for (j in 0..len2) matrix[0][j] = j
        
        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (str1[i - 1] == str2[j - 1]) 0 else 1
                matrix[i][j] = minOf(
                    matrix[i - 1][j] + 1,      // deletion
                    matrix[i][j - 1] + 1,      // insertion
                    matrix[i - 1][j - 1] + cost // substitution
                )
            }
        }
        
        return matrix[len1][len2]
    }

    suspend fun lyrics(
        artist: String,
        title: String,
    ) = runCatching {
        queryLyrics(artist = artist, title = title, album = null)
    }

    @JvmInline
    value class Lyrics(
        val text: String,
    ) {
        val sentences
            get() =
                runCatching {
                    buildMap {
                        put(0L, "")
                        text.trim().lines().filter { it.length >= 10 }.forEach {
                            put(
                                it[8].digitToInt() * 10L +
                                    it[7].digitToInt() * 100 +
                                    it[5].digitToInt() * 1000 +
                                    it[4].digitToInt() * 10000 +
                                    it[2].digitToInt() * 60 * 1000 +
                                    it[1].digitToInt() * 600 * 1000,
                                it.substring(10),
                            )
                        }
                    }
                }.getOrNull()
    }
}


