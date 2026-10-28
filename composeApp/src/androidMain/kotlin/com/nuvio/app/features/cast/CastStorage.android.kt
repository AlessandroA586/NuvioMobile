package com.nuvio.app.features.cast

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

actual object CastStorage {
    private const val PREFS_NAME = "nuvio_cast_storage"
    private const val KEY_MANUAL_DEVICES = "manual_cast_devices"

    private var preferences: SharedPreferences? = null
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    actual fun loadManualDevices(): List<CastDevice> {
        val raw = preferences?.getString(KEY_MANUAL_DEVICES, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<CastDevice>>(raw)
        }.getOrDefault(emptyList())
    }

    actual fun saveManualDevices(devices: List<CastDevice>) {
        val prefs = preferences ?: return
        val raw = runCatching { json.encodeToString(devices) }.getOrNull()
        prefs.edit().apply {
            if (raw != null) {
                putString(KEY_MANUAL_DEVICES, raw)
            } else {
                remove(KEY_MANUAL_DEVICES)
            }
            apply()
        }
    }
}
