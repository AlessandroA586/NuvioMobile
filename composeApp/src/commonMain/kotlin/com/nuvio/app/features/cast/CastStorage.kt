package com.nuvio.app.features.cast

expect object CastStorage {
    fun loadManualDevices(): List<CastDevice>
    fun saveManualDevices(devices: List<CastDevice>)
}
