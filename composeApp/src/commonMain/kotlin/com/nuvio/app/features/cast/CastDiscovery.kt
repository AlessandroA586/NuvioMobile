package com.nuvio.app.features.cast

expect object CastDiscovery {
    fun startDiscovery(onDeviceFound: (CastDevice) -> Unit)
    fun stopDiscovery()
}
