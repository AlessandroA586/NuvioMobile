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

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var storageLoaded = false

    fun initStorage() {
        if (storageLoaded) return
        storageLoaded = true
        val savedManual = runCatching { CastStorage.loadManualDevices() }.getOrDefault(emptyList())
        if (savedManual.isNotEmpty()) {
            val current = _availableDevices.value.toMutableList()
            for (saved in savedManual) {
                if (current.none { it.id == saved.id || (it.host == saved.host && it.port == saved.port) }) {
                    current.add(saved)
                }
            }
            _availableDevices.value = current
        }
    }

    fun startDiscovery() {
        initStorage()
        _isScanning.value = true
        CastDiscovery.startDiscovery { device ->
            registerDevice(device)
        }
        // Automatically stop the scanning indicator after 8 seconds
        scope.launch {
            delay(8000)
            _isScanning.value = false
        }
    }

    fun stopDiscovery() {
        _isScanning.value = false
        CastDiscovery.stopDiscovery()
    }

    fun registerDevice(device: CastDevice) {
        val current = _availableDevices.value.toMutableList()
        val index = current.indexOfFirst { it.id == device.id || (it.host == device.host && it.port == device.port) }
        if (index >= 0) {
            val existing = current[index]
            // Keep isManual flag if previously set, but update name and model
            current[index] = device.copy(
                isManual = existing.isManual || device.isManual,
                name = if (device.name.isNotBlank()) device.name else existing.name,
            )
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

    fun addManualDevice(host: String, port: Int = 8765, name: String = "Smart TV (Manual)") {
        val cleanHost = host.trim()
        if (cleanHost.isBlank()) return
        val device = CastDevice(
            id = "manual_${cleanHost}_$port",
            name = name,
            host = cleanHost,
            port = port,
            model = "Nuvio Legacy TV",
            isLegacy = true,
            isManual = true,
        )
        registerDevice(device)
        selectDevice(device)
        saveManualDevices()
    }

    fun removeDevice(device: CastDevice) {
        val current = _availableDevices.value.toMutableList()
        current.removeAll { it.id == device.id || (it.host == device.host && it.port == device.port) }
        _availableDevices.value = current
        if (_selectedDevice.value?.id == device.id) {
            selectDevice(null)
        }
        saveManualDevices()
    }

    fun clearManualDevices() {
        val current = _availableDevices.value.filter { !it.isManual && !it.id.startsWith("manual_") }
        _availableDevices.value = current
        if (_selectedDevice.value?.isManual == true || _selectedDevice.value?.id?.startsWith("manual_") == true) {
            selectDevice(null)
        }
        saveManualDevices()
    }

    private fun saveManualDevices() {
        val manualOnly = _availableDevices.value.filter { it.isManual || it.id.startsWith("manual_") }
        runCatching { CastStorage.saveManualDevices(manualOnly) }
    }

    fun castStream(
        title: String,
        streamUrl: String,
        imdbId: String? = null,
        videoId: String? = null,
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
                videoId = videoId,
                type = type,
                season = season,
                episode = episode,
                streamUrl = streamUrl,
                addonId = addonId,
                sourceName = sourceName,
                headers = headers,
                startTimeSeconds = startTimeSeconds,
                positionMs = startTimeSeconds * 1000,
            )

            val result = CastClient.sendMedia(device, payload)
            _isConnecting.value = false

            result.onSuccess {
                _selectedDevice.value = device
                _playbackState.value = CastStatusResponse(
                    state = CastPlaybackState.PLAYING,
                    positionSeconds = startTimeSeconds,
                    positionMs = startTimeSeconds * 1000,
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
            CastClient.sendCommand(device, CastControlCommand("RESUME"))
            _playbackState.value = _playbackState.value.copy(state = CastPlaybackState.PLAYING)
        }
    }

    fun seekTo(seconds: Long) {
        val device = _selectedDevice.value ?: return
        scope.launch {
            CastClient.sendCommand(device, CastControlCommand("SEEK", positionSeconds = seconds, positionMs = seconds * 1000))
            _playbackState.value = _playbackState.value.copy(positionSeconds = seconds, positionMs = seconds * 1000)
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
