/*
 * Velune Project
 * Stream extraction backed by InnerTubeX (github.com/MetrolistGroup/innertubex).
 * Adapted from Metrolist's InnerTubeXPlayer (github.com/mostafaalagamy/Metrolist).
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.nikhil.yt.utils

import android.content.Context
import android.net.ConnectivityManager
import com.metrolist.innertubex.InnerTubeLogEvent
import com.metrolist.innertubex.InnerTubeLogLevel
import com.metrolist.innertubex.InnerTubeLogger
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.ExtractedStream
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.PoTokenResult
import com.metrolist.innertubex.extraction.StreamResolveException
import com.metrolist.innertubex.extraction.TokenProvider
import com.metrolist.innertubex.extraction.TokenProviderCapabilities
import com.metrolist.innertubex.extraction.YtConfigParser
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.extraction.generateClientPlaybackNonce
import com.metrolist.innertubex.extraction.strategy.PoTokenProviderKind
import com.nikhil.yt.constants.AudioQuality
import com.nikhil.yt.innertube.YouTube
import com.nikhil.yt.innertube.models.Thumbnail
import com.nikhil.yt.innertube.models.Thumbnails
import com.nikhil.yt.innertube.models.response.PlayerResponse
import com.nikhil.yt.utils.potoken.PoTokenGenerator
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.time.ExperimentalTime
import com.metrolist.innertubex.InnerTube as InnerTubeX
import com.metrolist.innertubex.extraction.AudioQuality as InnerTubeXAudioQuality
import com.metrolist.innertubex.models.YouTubeLocale as InnerTubeXLocale

/**
 * Resolves playable audio streams with InnerTubeX: client selection, PO tokens minted through a
 * BotGuard WebView, and signature / n-parameter transforms. The result is mapped to
 * [YTPlayerUtils.PlaybackData] so the playback and download pipelines stay unchanged.
 */
object InnerTubeXPlayer {
    private const val TAG = "InnerTubeXPlayer"
    private const val STREAM_CLIENT_FAILURE_TTL_MS = 5 * 60 * 1000L
    private const val DEFAULT_STREAM_TTL_SECONDS = 5 * 60
    private const val PLAYER_CONFIG_URL =
        "https://raw.githubusercontent.com/ZemerTeam/zemer-cipher/master/library/src/main/assets/player_configs.json"

    @Volatile
    private var applicationContext: Context? = null

    @Volatile
    private var transport: Transport? = null
    private var transportGeneration = 0L

    @Volatile
    private var currentBundle: ExtractionBundle? = null
    private val bundleMutex = Mutex()

    private val streamClientFailures = ConcurrentHashMap<String, FailedStreamClients>()

    fun initialize(context: Context) {
        if (applicationContext == null) applicationContext = context.applicationContext
    }

    val isInitialized: Boolean
        get() = applicationContext != null

    suspend fun prewarm() = bundle().extractor.prewarm()

    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        networkMetered: Boolean? = null,
        avoidCodecs: Set<String> = emptySet(),
        allowBoundedRange: Boolean = true,
    ): Result<YTPlayerUtils.PlaybackData> =
        try {
            val hints =
                ContentHints(
                    isUploaded = playlistId?.contains("MLPT") == true,
                ).withStreamCapabilities(
                    allowHls = false,
                    allowSabr = false,
                    allowBoundedRange = allowBoundedRange,
                )
            val isMetered = networkMetered ?: connectivityManager.isActiveNetworkMetered
            val stream =
                requireNotNull(
                    bundle().extractor.extract(
                        videoId = videoId,
                        hints = hints,
                        excludedClients = failedStreamClients(videoId),
                        audioQuality = audioQuality.toInnerTubeX(isMetered, avoidCodecs),
                        clientPlaybackNonce = generateClientPlaybackNonce(),
                    ),
                ) { "InnerTubeX returned no playable stream" }
            check(stream.sabrBootstrap == null) { "SABR is not supported by this playback engine" }
            Timber.tag(TAG).i("Resolved $videoId with client ${stream.clientName} (${stream.profileId}), itag ${stream.itag}")
            Result.success(stream.toPlaybackData())
        } catch (error: CancellationException) {
            throw error
        } catch (error: StreamResolveException) {
            Timber.tag(TAG).w("Stream resolution failed for $videoId: reason=${error.reason}, message=${error.message}")
            val cause = error.cause
            Result.failure(
                if (error.reason == StreamResolveException.Reason.NETWORK && cause != null) cause else error,
            )
        } catch (error: Exception) {
            Timber.tag(TAG).e(error, "Unexpected InnerTubeX failure for $videoId")
            Result.failure(error)
        }

    /** Excludes [clientName] for [videoId] on the next resolutions, e.g. after a 403 from the CDN. */
    fun markStreamClientFailed(
        videoId: String,
        clientName: String,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        streamClientFailures.compute(videoId) { _, failures ->
            val previous = failures?.takeIf { nowMs - it.failedAtMs in 0 until STREAM_CLIENT_FAILURE_TTL_MS }
            FailedStreamClients(previous?.clientNames.orEmpty() + clientName, nowMs)
        }
    }

    fun clearStreamClientFailures() {
        streamClientFailures.clear()
    }

    /** Refreshes the remote cipher configuration after a stream was rejected; true when it changed. */
    suspend fun refreshAfterStreamRejection(): Boolean = bundle().cipherService.refreshAfterStreamRejection()

    private fun failedStreamClients(
        videoId: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Set<String> {
        val failures = streamClientFailures[videoId] ?: return emptySet()
        if ((nowMs - failures.failedAtMs) !in 0 until STREAM_CLIENT_FAILURE_TTL_MS) {
            streamClientFailures.remove(videoId, failures)
            return emptySet()
        }
        return failures.clientNames
    }

    private suspend fun bundle(): ExtractionBundle {
        val latestTransport = currentTransport()
        currentBundle?.takeIf { it.transportGeneration == latestTransport.generation }?.let { return it }
        return bundleMutex.withLock {
            val lockedTransport = currentTransport()
            currentBundle?.takeIf { it.transportGeneration == lockedTransport.generation }?.let { return@withLock it }
            try {
                currentBundle?.cipherService?.dispose()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.tag(TAG).w("Old cipher service disposal failed: ${error::class.simpleName}")
            }

            val remoteStore = RemotePlayerConfigStore(lockedTransport.httpClient, configRepository, logger)
            val cipherService = YouTubeCipherService(lockedTransport.httpClient, remoteStore, logger)
            val extractor =
                InnerTubeExtractor(
                    configParser =
                        YtConfigParserImpl(
                            lockedTransport.httpClient,
                            lockedTransport.innerTube,
                            remoteStore,
                            logger,
                        ).withEmbeddedConfigFallback(),
                    cipherService = cipherService,
                    innerTube = lockedTransport.innerTube,
                    tokenProvider = tokenProvider,
                    logger = logger,
                )
            ExtractionBundle(lockedTransport.generation, cipherService, extractor).also { currentBundle = it }
        }
    }

    /** Returns the InnerTubeX transport for the current proxy, with Velune's session copied into it. */
    @Synchronized
    private fun currentTransport(): Transport {
        val proxy = YouTube.proxy
        val existing = transport
        val active =
            if (existing != null && existing.proxy == proxy) {
                existing
            } else {
                existing?.let { old ->
                    runCatching { old.innerTube.close() }
                    runCatching { old.httpClient.close() }
                }
                val httpClient = createHttpClient(proxy)
                Transport(
                    proxy = proxy,
                    httpClient = httpClient,
                    innerTube = InnerTubeX(httpClient, logger = logger),
                    generation = ++transportGeneration,
                ).also { transport = it }
            }
        syncSession(active.innerTube)
        return active
    }

    private fun syncSession(innerTube: InnerTubeX) {
        val locale = YouTube.locale
        innerTube.locale = InnerTubeXLocale(gl = locale.gl, hl = locale.hl)
        innerTube.replaceSession(
            cookie = YouTube.cookie?.takeIf { it.isNotBlank() },
            // Keep the visitor data InnerTubeX fetched itself while Velune has none yet.
            visitorData = YouTube.visitorData?.takeIf { it.isNotBlank() && it != "null" } ?: innerTube.visitorData,
            dataSyncId = YouTube.dataSyncId?.takeIf { it.isNotBlank() },
            authUser = innerTube.authUser,
            useLoginForBrowse = false,
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun createHttpClient(proxy: Proxy?) =
        HttpClient(OkHttp) {
            // InnerTubeX validates statuses and retries transient failures itself.
            expectSuccess = false

            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        explicitNulls = false
                        encodeDefaults = true
                    },
                )
            }

            install(ContentEncoding) {
                gzip(0.9F)
                deflate(0.8F)
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 60_000
            }

            engine {
                config {
                    connectTimeout(30, TimeUnit.SECONDS)
                    readTimeout(60, TimeUnit.SECONDS)
                    writeTimeout(60, TimeUnit.SECONDS)
                    retryOnConnectionFailure(true)
                    proxy?.let(::proxy)
                }
            }

            defaultRequest {
                url("https://music.youtube.com/youtubei/v1/")
                header("Accept", "application/json")
                header("Cache-Control", "no-cache")
            }
        }

    private val configRepository: PlayerConfigRepository by lazy {
        AndroidPlayerConfigRepository(requireNotNull(applicationContext) { "InnerTubeXPlayer is not initialized" })
    }

    private val poTokenGenerator: PoTokenGenerator by lazy {
        PoTokenGenerator(requireNotNull(applicationContext) { "InnerTubeXPlayer is not initialized" })
    }

    private val tokenProvider =
        object : TokenProvider {
            override val capabilities =
                TokenProviderCapabilities(
                    providers = setOf(PoTokenProviderKind.WEB_BOTGUARD),
                    usesWebView = true,
                )

            override suspend fun getPoToken(
                videoId: String,
                visitorData: String,
                cookie: String?,
            ): PoTokenResult? =
                poTokenGenerator.getWebClientPoToken(videoId, visitorData)?.let { token ->
                    PoTokenResult(
                        playerRequestToken = token.playerRequestPoToken,
                        streamingDataToken = token.streamingDataPoToken,
                        visitorData = visitorData,
                    )
                }

            override suspend fun close() {
                poTokenGenerator.close()
            }
        }

    private val logger =
        InnerTubeLogger { event: InnerTubeLogEvent ->
            val details =
                event.details.entries.joinToString(prefix = " [", postfix = "]") { "${it.key}=${it.value}" }
            val message = event.message + details.takeUnless { event.details.isEmpty() }.orEmpty()
            when (event.level) {
                InnerTubeLogLevel.DEBUG -> Timber.tag(event.tag).d(message)
                InnerTubeLogLevel.INFO -> Timber.tag(event.tag).i(message)
                InnerTubeLogLevel.WARN -> Timber.tag(event.tag).w(message)
                InnerTubeLogLevel.ERROR -> Timber.tag(event.tag).e(message)
            }
        }

    private class Transport(
        val proxy: Proxy?,
        val httpClient: HttpClient,
        val innerTube: InnerTubeX,
        val generation: Long,
    )

    private class ExtractionBundle(
        val transportGeneration: Long,
        val cipherService: YouTubeCipherService,
        val extractor: InnerTubeExtractor,
    )

    private data class FailedStreamClients(
        val clientNames: Set<String>,
        val failedAtMs: Long,
    )

    /** Persists the remote cipher configuration table between app launches. */
    private class AndroidPlayerConfigRepository(context: Context) : PlayerConfigRepository {
        private val preferences = context.getSharedPreferences("innertubex_player_config", Context.MODE_PRIVATE)

        override val enabled: Boolean = true
        override val sourceUrl: String = PLAYER_CONFIG_URL
        override val defaultSourceUrl: String = PLAYER_CONFIG_URL
        override var cachedJson: String
            get() = preferences.getString("json", "").orEmpty()
            set(value) = preferences.edit().putString("json", value).apply()
        override var cachedAtMs: Long
            get() = preferences.getLong("cached_at_ms", 0L)
            set(value) = preferences.edit().putLong("cached_at_ms", value).apply()
        override var cachedSourceUrl: String
            get() = preferences.getString("source_url", "").orEmpty()
            set(value) = preferences.edit().putString("source_url", value).apply()
        override var cachedEtag: String
            get() = preferences.getString("etag", "").orEmpty()
            set(value) = preferences.edit().putString("etag", value).apply()
    }

    private fun YtConfigParser.withEmbeddedConfigFallback(): YtConfigParser =
        object : YtConfigParser by this {
            override suspend fun fetchConfig(
                videoId: String,
                useLoginCookies: Boolean,
            ) = try {
                this@withEmbeddedConfigFallback.fetchConfig(videoId, useLoginCookies)
            } catch (_: IllegalStateException) {
                this@withEmbeddedConfigFallback.fetchEmbeddedConfig(videoId, useLoginCookies = false)
            }
        }

    private fun AudioQuality.toInnerTubeX(
        isMetered: Boolean,
        avoidCodecs: Set<String>,
    ): InnerTubeXAudioQuality {
        // Same targets as the legacy selector: HIGH is the regular ~128-160 kbps stream, HIGHEST the
        // best available (Premium formats included), and AUTO picks HIGH on metered networks.
        val quality =
            when (this) {
                AudioQuality.LOW -> InnerTubeXAudioQuality.LOW
                AudioQuality.HIGH -> InnerTubeXAudioQuality.AUTO
                AudioQuality.HIGHEST -> InnerTubeXAudioQuality.HIGH
                AudioQuality.AUTO -> if (isMetered) InnerTubeXAudioQuality.AUTO else InnerTubeXAudioQuality.HIGH
            }
        // Devices without an Opus decoder need the AAC (MP4) formats.
        return if ("opus" in avoidCodecs && quality != InnerTubeXAudioQuality.LOW) InnerTubeXAudioQuality.MP4 else quality
    }

    @OptIn(ExperimentalTime::class)
    private fun ExtractedStream.toPlaybackData(): YTPlayerUtils.PlaybackData {
        val metadata = mediaMetadata
        val fullMimeType =
            if (codecs.isNullOrBlank()) mimeType.orEmpty() else "${mimeType.orEmpty()}; codecs=\"$codecs\""
        return YTPlayerUtils.PlaybackData(
            audioConfig =
                if (loudnessDb != null || perceptualLoudnessDb != null) {
                    PlayerResponse.PlayerConfig.AudioConfig(loudnessDb, perceptualLoudnessDb)
                } else {
                    null
                },
            videoDetails =
                metadata?.let {
                    PlayerResponse.VideoDetails(
                        videoId = videoId,
                        title = it.title.orEmpty(),
                        author = it.author.orEmpty(),
                        channelId = it.channelId.orEmpty(),
                        lengthSeconds = it.durationSeconds?.toString().orEmpty(),
                        musicVideoType = it.musicVideoType,
                        viewCount = it.viewCount.orEmpty(),
                        thumbnail =
                            Thumbnails(
                                it.thumbnails.map { thumbnail ->
                                    Thumbnail(thumbnail.url, thumbnail.width, thumbnail.height)
                                },
                            ),
                    )
                }?.takeIf { it.title.isNotBlank() },
            playbackTracking =
                playbackTracking?.let {
                    PlayerResponse.PlaybackTracking(
                        videostatsPlaybackUrl = it.playbackUrl?.let(PlayerResponse.PlaybackTracking::VideostatsPlaybackUrl),
                        videostatsWatchtimeUrl = it.watchtimeUrl?.let(PlayerResponse.PlaybackTracking::VideostatsWatchtimeUrl),
                        atrUrl = null,
                    )
                },
            format =
                PlayerResponse.StreamingData.Format(
                    itag = itag,
                    url = audioUrl,
                    mimeType = fullMimeType,
                    bitrate = bitrate ?: 0,
                    width = null,
                    height = null,
                    contentLength = contentLengthBytes,
                    quality = "",
                    fps = null,
                    qualityLabel = null,
                    averageBitrate = bitrate,
                    audioQuality = null,
                    approxDurationMs = metadata?.durationSeconds?.times(1000)?.toString(),
                    audioSampleRate = sampleRate,
                    audioChannels = null,
                    loudnessDb = loudnessDb,
                    lastModified = null,
                    signatureCipher = null,
                    cipher = null,
                ),
            streamUrl = audioUrl,
            streamExpiresInSeconds =
                expiresAt
                    ?.let { ((it.toEpochMilliseconds() - System.currentTimeMillis()) / 1000L).toInt() }
                    ?.coerceAtLeast(1)
                    ?: DEFAULT_STREAM_TTL_SECONDS,
            streamClient = clientName,
            streamHeaders = headers,
            rangeChunkSizeBytes = rangeChunkSizeBytes.takeIf { requireBoundedRange || useRangeChunks },
        )
    }
}
