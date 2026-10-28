package com.nuvio.app.features.cast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

actual object CastDiscovery {
    private const val TAG = "NuvioCastDiscovery"
    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var scanJob: Job? = null
    private val activeNsdListeners = mutableListOf<NsdManager.DiscoveryListener>()
    private var multicastLock: WifiManager.MulticastLock? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    actual fun startDiscovery(onDeviceFound: (CastDevice) -> Unit) {
        stopDiscovery()
        try {
            val wifi = appContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("NuvioCastMulticastLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (_: Exception) {}
        startNsdDiscovery(onDeviceFound)
        startSubnetScan(onDeviceFound)
    }

    actual fun stopDiscovery() {
        scanJob?.cancel()
        scanJob = null
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {}
        multicastLock = null

        val context = appContext
        if (context != null) {
            val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            if (nsdManager != null) {
                synchronized(activeNsdListeners) {
                    for (listener in activeNsdListeners) {
                        try {
                            nsdManager.stopServiceDiscovery(listener)
                        } catch (_: Exception) {}
                    }
                    activeNsdListeners.clear()
                }
            }
        }
    }

    private fun startNsdDiscovery(onDeviceFound: (CastDevice) -> Unit) {
        val context = appContext ?: return
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return

        val serviceTypes = listOf("_nuviocast._tcp.", "_googlecast._tcp.")

        for (serviceType in serviceTypes) {
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {
                    Log.d(TAG, "NSD discovery started: $regType")
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    Log.d(TAG, "NSD service found: ${serviceInfo.serviceName} (${serviceInfo.serviceType})")
                    try {
                        nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(service: NsdServiceInfo?, errorCode: Int) {
                                Log.w(TAG, "NSD resolve failed for ${service?.serviceName}: error $errorCode")
                            }

                            override fun onServiceResolved(resolved: NsdServiceInfo) {
                                val host = resolved.host?.hostAddress ?: return
                                if (host.startsWith("127.") || host == "::1") return
                                val port = resolved.port

                                var friendlyName = resolved.serviceName
                                if (resolved.serviceType.contains("googlecast")) {
                                    val fnBytes = resolved.attributes["fn"]
                                    if (fnBytes != null) {
                                        val fn = String(fnBytes, Charsets.UTF_8).trim()
                                        if (fn.isNotBlank()) friendlyName = fn
                                    }
                                }

                                val device = CastDevice(
                                    id = "nsd_${host}_$port",
                                    name = friendlyName,
                                    host = host,
                                    port = port,
                                    model = if (resolved.serviceType.contains("googlecast")) "Chromecast / Google TV" else "Nuvio Legacy TV",
                                    isLegacy = true,
                                    isManual = false,
                                )
                                onDeviceFound(device)
                            }
                        })
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed resolving service ${serviceInfo.serviceName}: ${e.message}")
                    }
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    try { nsdManager.stopServiceDiscovery(this) } catch (_: Exception) {}
                }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            }

            try {
                nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
                synchronized(activeNsdListeners) {
                    activeNsdListeners.add(listener)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not start NSD for $serviceType: ${e.message}")
            }
        }
    }

    private fun startSubnetScan(onDeviceFound: (CastDevice) -> Unit) {
        scanJob = scope.launch {
            val localIps = getLocalSubnetPrefixes()
            if (localIps.isEmpty()) return@launch

            for (prefix in localIps) {
                if (!isActive) break
                val ports = listOf(8765, 8920, 8008)

                // Scan in batches of 25 IPs for rapid non-blocking resolution
                val allIps = (1..254).map { "$prefix$it" }
                val batches = allIps.chunked(25)

                for (batch in batches) {
                    if (!isActive) break
                    val deferreds = batch.map { ip ->
                        async {
                            for (port in ports) {
                                if (!isActive) break
                                checkIpAndPort(ip, port, onDeviceFound)
                            }
                        }
                    }
                    deferreds.awaitAll()
                    delay(10)
                }
            }
        }
    }

    private fun checkIpAndPort(ip: String, port: Int, onDeviceFound: (CastDevice) -> Unit) {
        var socket: Socket? = null
        try {
            socket = Socket()
            socket.connect(InetSocketAddress(ip, port), 400)

            // Connection succeeded! Query device status/info
            if (port == 8765 || port == 8920) {
                socket.soTimeout = 800
                val writer = socket.getOutputStream()
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val getReq = "GET /status HTTP/1.1\r\nHost: $ip:$port\r\nUser-Agent: NuvioMobile\r\nConnection: close\r\n\r\n"
                writer.write(getReq.toByteArray(Charsets.UTF_8))
                writer.flush()

                val line1 = reader.readLine()
                if (line1 != null && line1.contains("200")) {
                    var contentLength = 0
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        if (line.isNullOrEmpty()) break
                        if (line!!.lowercase().startsWith("content-length:")) {
                            contentLength = line!!.substringAfter(':').trim().toIntOrNull() ?: 0
                        }
                    }
                    val body = if (contentLength > 0) {
                        val buffer = CharArray(contentLength)
                        var readTotal = 0
                        while (readTotal < contentLength) {
                            val r = reader.read(buffer, readTotal, contentLength - readTotal)
                            if (r < 0) break
                            readTotal += r
                        }
                        String(buffer, 0, readTotal)
                    } else {
                        try { reader.readText() } catch (_: Exception) { "" }
                    }
                    val json = runCatching { JSONObject(body) }.getOrNull()
                    val devName = json?.optString("device_name", "")?.takeIf { it.isNotBlank() } ?: "Nuvio TV ($ip)"
                    val devId = json?.optString("device_id", "")?.takeIf { it.isNotBlank() } ?: "tv_${ip}_$port"
                    val device = CastDevice(
                        id = devId,
                        name = devName,
                        host = ip,
                        port = port,
                        model = "Nuvio Legacy TV",
                        isLegacy = true,
                        isManual = false,
                    )
                    onDeviceFound(device)
                    return
                }
            } else if (port == 8008) {
                // DIAL / Google Cast discovery on port 8008
                socket.soTimeout = 800
                val writer = socket.getOutputStream()
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val getReq = "GET /ssdp/device-desc.xml HTTP/1.1\r\nHost: $ip:8008\r\nConnection: close\r\n\r\n"
                writer.write(getReq.toByteArray(Charsets.UTF_8))
                writer.flush()

                val content = reader.readText()
                if (content.contains("<friendlyName>")) {
                    val fn = content.substringAfter("<friendlyName>").substringBefore("</friendlyName>").trim()
                    if (fn.isNotBlank()) {
                        val device = CastDevice(
                            id = "dial_${ip}_8008",
                            name = fn,
                            host = ip,
                            port = port,
                            model = "Smart TV (DIAL)",
                            isLegacy = true,
                            isManual = false,
                        )
                        onDeviceFound(device)
                    }
                }
            }
        } catch (_: Exception) {
            // Port closed or timed out, ignore
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    private fun getLocalSubnetPrefixes(): List<String> {
        val prefixes = mutableListOf<String>()
        try {
            val wifi = appContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ipInt = wifi?.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                val ipStr = String.format(
                    java.util.Locale.US,
                    "%d.%d.%d.",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff
                )
                if (!prefixes.contains(ipStr)) {
                    prefixes.add(ipStr)
                }
            }
        } catch (_: Exception) {}

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return prefixes
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            val lastDot = ip.lastIndexOf('.')
                            if (lastDot > 0) {
                                val prefix = ip.substring(0, lastDot + 1)
                                if (!prefixes.contains(prefix)) {
                                    prefixes.add(prefix)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed retrieving local interfaces: ${e.message}")
        }
        return prefixes
    }
}
