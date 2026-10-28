package com.nuvio.app.features.cast

actual object CastStorage {
    private var inMemoryDevices: List<CastDevice> = emptyList()

    actual fun loadManualDevices(): List<CastDevice> {
        return inMemoryDevices
    }

    actual fun saveManualDevices(devices: List<CastDevice>) {
        inMemoryDevices = devices
    }
}
