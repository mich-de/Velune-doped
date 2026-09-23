/*
 * Velune Project
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.nikhil.yt.utils

import com.nikhil.yt.innertube.YouTube
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Translates lyrics lines through Google's `translate_a/t` endpoint (the one used by Chrome's
 * translate extension). Each `q` parameter comes back in the same order, so a whole song needs a
 * single request and no separator tricks.
 */
object LyricsTranslator {
    private const val ENDPOINT = "https://translate.googleapis.com/translate_a/t"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
    private const val MAX_LINES_PER_REQUEST = 100
    private const val MAX_CHARS_PER_REQUEST = 8_000
    private const val MAX_CHARS_PER_PASSAGE = 4_000

    /** Google answered HTTP 429: it is rate limiting translation requests from this network. */
    class RateLimitedException : IOException("HTTP 429")

    private val client by lazy {
        OkHttpClient.Builder()
            .proxy(YouTube.proxy)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** Translates [lines] into [targetLanguageCode]; the result has one entry per input line. */
    fun translateLines(lines: List<String>, targetLanguageCode: String): List<String> {
        val singleLines = lines.map { it.replace('\n', ' ') }
        // Google has no Neapolitan: normalize it towards Italian and pivot through Corsican.
        val neapolitan = NeapolitanText.looksNeapolitan(singleLines)
        val prepared = if (neapolitan) singleLines.map(NeapolitanText::normalize) else singleLines
        val sourceLanguageCode = if (neapolitan) NeapolitanText.PIVOT_LANGUAGE_CODE else "auto"
        return translatePassages(prepared, sourceLanguageCode, targetLanguageCode)
            ?: translateTexts(prepared, sourceLanguageCode, targetLanguageCode)
    }

    /**
     * Sends the lyrics as whole passages, so one language is detected for the song and every line
     * keeps its context. Returns null when Google merged or split lines of a passage.
     */
    private fun translatePassages(
        lines: List<String>,
        sourceLanguageCode: String,
        targetLanguageCode: String,
    ): List<String>? {
        val passages = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        var chars = 0
        for (line in lines) {
            if (current.isNotEmpty() && chars + line.length + 1 > MAX_CHARS_PER_PASSAGE) {
                passages += current
                current = mutableListOf()
                chars = 0
            }
            current += line
            chars += line.length + 1
        }
        if (current.isNotEmpty()) passages += current

        val translatedPassages =
            translateTexts(passages.map { it.joinToString("\n") }, sourceLanguageCode, targetLanguageCode)
        val translated = ArrayList<String>(lines.size)
        for ((passage, translatedPassage) in passages.zip(translatedPassages)) {
            val translatedLines = translatedPassage.split('\n')
            if (translatedLines.size != passage.size) return null
            translatedLines.mapTo(translated) { it.trim() }
        }
        return translated
    }

    /** Translates each of [texts] separately, batching as many as fit in one request. */
    private fun translateTexts(
        texts: List<String>,
        sourceLanguageCode: String,
        targetLanguageCode: String,
    ): List<String> {
        val translated = ArrayList<String>(texts.size)
        var start = 0
        while (start < texts.size) {
            var end = start
            var chars = 0
            while (end < texts.size && end - start < MAX_LINES_PER_REQUEST &&
                (end == start || chars + texts[end].length <= MAX_CHARS_PER_REQUEST)
            ) {
                chars += texts[end].length
                end++
            }
            translated += translateBatch(texts.subList(start, end), sourceLanguageCode, targetLanguageCode)
            start = end
        }
        return translated
    }

    private fun translateBatch(
        lines: List<String>,
        sourceLanguageCode: String,
        targetLanguageCode: String,
    ): List<String> {
        val url =
            ENDPOINT.toHttpUrl().newBuilder()
                .addQueryParameter("client", "dict-chrome-ex")
                .addQueryParameter("sl", sourceLanguageCode)
                .addQueryParameter("tl", targetLanguageCode)
                .build()
        val body = FormBody.Builder().apply { lines.forEach { add("q", it) } }.build()
        val request =
            Request.Builder()
                .url(url)
                .post(body)
                .header("User-Agent", USER_AGENT)
                .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 429) throw RateLimitedException()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val items = Json.parseToJsonElement(response.body?.string().orEmpty()).jsonArray
            if (items.size != lines.size) {
                throw IOException("Expected ${lines.size} translations, got ${items.size}")
            }
            // With sl=auto every item is [translation, detectedLanguage]; otherwise a plain string.
            return items.mapIndexed { index, item ->
                val text =
                    when (item) {
                        is JsonArray -> (item.firstOrNull() as? JsonPrimitive)?.contentOrNull
                        is JsonPrimitive -> item.contentOrNull
                        else -> null
                    }
                text ?: lines[index]
            }
        }
    }
}
