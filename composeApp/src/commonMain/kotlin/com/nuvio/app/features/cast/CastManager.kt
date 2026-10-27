package com.nuvio.app.features.cast

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object CastManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollingJob: Job? = null

    private val _availableDevices = MutableStateFlow<List<CastDevice>>(emptyList())
    val availableDevices: StateFlow<List<CastDevice>> = _availableDevices.asStateFlow()

    private val _selectedDevice = MutableStateFlow<CastDevice?>(null)
    val selectedDevice: StateFlow<CastDevice?> = _selectedDevice.asStateFlow()

    private val _playbackState = MutableStateFlow(CastStatusResponse())
    val playbackState: StateFlow<CastStatusResponse> = _playbackState.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    fun registerDevice(device: CastDevice) {
        val current = _availableDevices.value.toMutableList()
        val index = current.indexOfFirst { it.id == device.id || it.host == device.host }
        if (index >= 0) {
            current[index] = device
        } else {
            current.add(device)
        }
        _availableDevices.value = current
    }

    fun selectDevice(device: CastDevice?) {
        _selectedDevice.value = device
        if (device == null) {
            stopPolling()
            _playbackState.value = CastStatusResponse()
        }
    }

    fun addManualDevice(host: String, port: Int = 8920, name: String = "Smart TV (Manual)") {
        val cleanHost = host.trim()
        if (cleanHost.isBlank()) return
        val device = CastDevice(
            id = "manual_$cleanHost",
            name = name,
            host = cleanHost,
            port = port,
            model = "Nuvio Legacy TV",
            isLegacy = true,
        )
        registerDevice(device)
        selectDevice(device)
    }

    fun castStream(
        title: String,
        streamUrl: String,
        imdbId: String? = null,
        type: String = "movie",
        season: Int? = null,
        episode: Int? = null,
        addonId: String? = null,
        sourceName: String? = null,
        headers: Map<String, String> = emptyMap(),
        startTimeSeconds: Long = 0,
        targetDevice: CastDevice? = _selectedDevice.value,
        onSuccess: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null,
    ) {
        val device = targetDevice ?: _selectedDevice.value
        if (device == null) {
            onError?.invoke(IllegalStateException("No TV device selected for casting"))
            return
        }

        scope.launch {
            _isConnecting.value = true
            val payload = CastMediaPayload(
                action = "PLAY_SOURCE",
                title = title,
                imdbId = imdbId,
                type = type,
                season = season,
                episode = episode,
                streamUrl = streamUrl,
                addonId = addonId,
                sourceName = sourceName,
                headers = headers,
                startTimeSeconds = startTimeSeconds,
            )

            val result = CastClient.sendMedia(device, payload)
            _isConnecting.value = false

            result.onSuccess {
                _selectedDevice.value = device
                _playbackState.value = CastStatusResponse(
                    state = CastPlaybackState.PLAYING,
                    positionSeconds = startTimeSeconds,
                    title = title,
                    sourceName = sourceName,
                )
                startPolling(device)
                onSuccess?.invoke()
            }.onFailure { err ->
                onError?.invoke(err)
            }
        }
    }

    fun pause() {
        val device = _selectedDevice.value ?: return
        scope.launch {
            CastClient.sendCommand(device, CastControlCommand("PAUSE"))
            _playbackState.value = _playbackState.value.copy(state = CastPlaybackState.PAUSED)
        }
    }

    fun resume() {
        val device = _selectedDevice.value ?: return
        scope.launch {
            CastClient.sendCommand(device, CastControlCommand("PLAY"))
            _playbackState.value = _playbackState.value.copy(state = CastPlaybackState.PLAYING)
        }
    }

    fun seekTo(seconds: Long) {
        val device = _selectedDevice.value ?: return
        scope.launch {
            CastClient.sendCommand(device, CastControlCommand("SEEK", positionSeconds = seconds))
            _playbackState.value = _playbackState.value.copy(positionSeconds = seconds)
        }
    }

    fun stop() {
        val device = _selectedDevice.value ?: return
        scope.launch {
            CastClient.sendCommand(device, CastControlCommand("STOP"))
            _playbackState.value = CastStatusResponse(state = CastPlaybackState.STOPPED)
            stopPolling()
        }
    }

    private fun startPolling(device: CastDevice) {
        stopPolling()
        pollingJob = scope.launch {
            while (isActive) {
                delay(2000)
                val status = CastClient.getStatus(device).getOrNull()
                if (status != null) {
                    _playbackState.value = status
                    if (status.state == CastPlaybackState.STOPPED) {
                        break
                    }
                }
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }
}
