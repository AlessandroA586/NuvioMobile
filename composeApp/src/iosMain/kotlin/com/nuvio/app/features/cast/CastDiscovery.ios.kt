package com.nuvio.app.features.cast

actual object CastDiscovery {
    actual fun startDiscovery(onDeviceFound: (CastDevice) -> Unit) {}
    actual fun stopDiscovery() {}
}
