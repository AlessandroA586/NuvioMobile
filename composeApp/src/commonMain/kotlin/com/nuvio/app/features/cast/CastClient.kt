package com.nuvio.app.features.cast

import com.nuvio.app.features.addons.httpRequestRaw
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object CastClient {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    suspend fun sendMedia(device: CastDevice, payload: CastMediaPayload): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val body = json.encodeToString(payload)
            val response = httpRequestRaw(
                method = "POST",
                url = "${device.endpointUrl}/api/cast/play",
                headers = mapOf(
                    "Content-Type" to "application/json",
                    "Accept" to "application/json",
                    "User-Agent" to "NuvioMobile-Cast/1.0",
                ),
                body = body,
            )
            response.status in 200..299
        }
    }

    suspend fun sendCommand(device: CastDevice, command: CastControlCommand): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val body = json.encodeToString(command)
            val response = httpRequestRaw(
                method = "POST",
                url = "${device.endpointUrl}/api/cast/command",
                headers = mapOf(
                    "Content-Type" to "application/json",
                    "Accept" to "application/json",
                    "User-Agent" to "NuvioMobile-Cast/1.0",
                ),
                body = body,
            )
            response.status in 200..299
        }
    }

    suspend fun getStatus(device: CastDevice): Result<CastStatusResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val response = httpRequestRaw(
                method = "GET",
                url = "${device.endpointUrl}/api/cast/status",
                headers = mapOf(
                    "Accept" to "application/json",
                    "User-Agent" to "NuvioMobile-Cast/1.0",
                ),
                body = "",
            )
            if (response.status in 200..299) {
                json.decodeFromString<CastStatusResponse>(response.body)
            } else {
                error("Status check failed: HTTP ${response.status}")
            }
        }
    }

    suspend fun pingDevice(host: String, port: Int = 8920): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val response = httpRequestRaw(
                method = "GET",
                url = "http://$host:$port/api/cast/ping",
                headers = mapOf("Accept" to "application/json"),
                body = "",
            )
            response.status in 200..299
        }.getOrDefault(false)
    }
}
