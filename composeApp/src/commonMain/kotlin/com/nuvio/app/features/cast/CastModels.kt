package com.nuvio.app.features.cast

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class CastPlaybackState {
    IDLE,
    CONNECTING,
    BUFFERING,
    PLAYING,
    PAUSED,
    STOPPED,
    ERROR,
}

@Serializable
data class CastDevice(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 8765,
    val model: String = "Smart TV (Nuvio Legacy)",
    val isLegacy: Boolean = true,
    val isManual: Boolean = false,
) {
    val endpointUrl: String
        get() = "http://$host:$port"
}

@Serializable
data class CastMediaPayload(
    @SerialName("action") val action: String = "PLAY_SOURCE",
    @SerialName("title") val title: String,
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("video_id") val videoId: String? = null,
    @SerialName("type") val type: String = "movie",
    @SerialName("season") val season: Int? = null,
    @SerialName("episode") val episode: Int? = null,
    @SerialName("stream_url") val streamUrl: String,
    @SerialName("addon_id") val addonId: String? = null,
    @SerialName("source_name") val sourceName: String? = null,
    @SerialName("headers") val headers: Map<String, String> = emptyMap(),
    @SerialName("start_time_seconds") val startTimeSeconds: Long = 0,
    @SerialName("position_ms") val positionMs: Long = startTimeSeconds * 1000,
)

@Serializable
data class CastControlCommand(
    @SerialName("command") val command: String, // PLAY, RESUME, PAUSE, SEEK, STOP
    @SerialName("position_seconds") val positionSeconds: Long? = null,
    @SerialName("position_ms") val positionMs: Long? = null,
)

@Serializable
data class CastStatusResponse(
    @SerialName("state") val state: CastPlaybackState = CastPlaybackState.IDLE,
    @SerialName("position_seconds") val positionSeconds: Long = 0,
    @SerialName("duration_seconds") val durationSeconds: Long = 0,
    @SerialName("position_ms") val positionMs: Long? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("source_name") val sourceName: String? = null,
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("status") val status: String? = null,
) {
    val effectivePositionSeconds: Long
        get() = positionMs?.let { it / 1000 } ?: positionSeconds

    val effectiveDurationSeconds: Long
        get() = durationMs?.let { it / 1000 } ?: durationSeconds
}
