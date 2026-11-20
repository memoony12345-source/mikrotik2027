package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import com.example.data.model.NetworkPortalState
import com.example.data.model.WifiPortalStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL

class NetworkPortalMonitor(context: Context) {

    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var probeJob: Job? = null

    private val _state = MutableStateFlow(NetworkPortalState())
    val state: StateFlow<NetworkPortalState> = _state.asStateFlow()

    @Volatile
    private var currentWifiNetwork: Network? = null

    private var isRegistered = false
    private var lastNotifiedUrl: String? = null

    var onCaptivePortalDetectedCallback: ((portalUrl: String, gatewayIp: String?) -> Unit)? = null

    private val wifiCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            currentWifiNetwork = network
            scheduleProbe(network)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                currentWifiNetwork = network
                scheduleProbe(network)
            }
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            if (currentWifiNetwork == network || isWifiNetwork(network)) {
                currentWifiNetwork = network
                scheduleProbe(network)
            }
        }

        override fun onLost(network: Network) {
            if (currentWifiNetwork == network) {
                currentWifiNetwork = null
                lastNotifiedUrl = null
                clearNetworkBinding()
                _state.value = NetworkPortalState(
                    status = WifiPortalStatus.NO_WIFI,
                    lastCheckedMs = System.currentTimeMillis(),
                )
            }
        }
    }

    @Synchronized
    fun startMonitoring() {
        if (isRegistered) {
            refreshNow()
            return
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        try {
            connectivityManager.registerNetworkCallback(request, wifiCallback)
            isRegistered = true
        } catch (_: Exception) {
        }
        refreshNow()
    }

    fun refreshNow() {
        val activeWifi = findCurrentWifiNetwork()
        if (activeWifi == null) {
            currentWifiNetwork = null
            _state.value = NetworkPortalState(
                status = WifiPortalStatus.NO_WIFI,
                lastCheckedMs = System.currentTimeMillis(),
            )
            return
        }
        currentWifiNetwork = activeWifi
        scheduleProbe(activeWifi)
    }

    fun bindToWifiNetwork(): Boolean {
        val net = currentWifiNetwork ?: findCurrentWifiNetwork() ?: return false
        return try {
            val bound = connectivityManager.bindProcessToNetwork(net)
            _state.value = _state.value.copy(isBoundToWifi = bound)
            bound
        } catch (_: Exception) {
            false
        }
    }

    fun clearNetworkBinding() {
        try {
            connectivityManager.bindProcessToNetwork(null)
        } catch (_: Exception) {
        }
    }

    suspend fun verifyInternetAfterLogin(): Boolean = withContext(Dispatchers.IO) {
        val net = currentWifiNetwork ?: findCurrentWifiNetwork() ?: return@withContext false
        val probe = probePortalUrl(net, gatewayIp = extractGatewayIp(connectivityManager.getLinkProperties(net)))
        val connected = probe is ProbeResult.InternetWorking
        if (connected) {
            clearNetworkBinding()
            _state.value = _state.value.copy(
                status = WifiPortalStatus.INTERNET_CONNECTED,
                isBoundToWifi = false,
                lastCheckedMs = System.currentTimeMillis(),
            )
        }
        connected
    }

    private fun isWifiNetwork(network: Network): Boolean {
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    @Suppress("DEPRECATION")
    private fun findCurrentWifiNetwork(): Network? {
        val active = connectivityManager.activeNetwork
        if (active != null && isWifiNetwork(active)) return active
        return connectivityManager.allNetworks.firstOrNull { isWifiNetwork(it) }
    }

    private fun scheduleProbe(network: Network) {
        probeJob?.cancel()
        probeJob = scope.launch {
            val caps = connectivityManager.getNetworkCapabilities(network)
            val linkProps = connectivityManager.getLinkProperties(network)
            val gatewayIp = extractGatewayIp(linkProps)
            val interfaceName = linkProps?.interfaceName

            _state.value = _state.value.copy(
                status = WifiPortalStatus.CHECKING,
                gatewayIp = gatewayIp,
                interfaceName = interfaceName,
                lastCheckedMs = System.currentTimeMillis(),
            )

            // 1. Check RFC 7710bis / 8908 Captive Portal API URL (Android 11+) if exposed by framework
            val apiPortalUrl = extractCaptivePortalApiUrl(linkProps)?.let {
                fetchRfc8908PortalUrl(network, it)
            }

            if (!apiPortalUrl.isNullOrBlank()) {
                val bound = tryBindWifi(network)
                _state.value = NetworkPortalState(
                    status = WifiPortalStatus.CAPTIVE_PORTAL_DETECTED,
                    gatewayIp = gatewayIp,
                    portalUrl = apiPortalUrl,
                    interfaceName = interfaceName,
                    detectionSource = "RFC 8908 Captive API",
                    isBoundToWifi = bound,
                    lastCheckedMs = System.currentTimeMillis(),
                )
                notifyIfNew(apiPortalUrl, gatewayIp)
                return@launch
            }

            // 2. Fast Wi-Fi-bound HTTP captive probe (outruns OS captive portal check)
            val probeResult = probePortalUrl(network, gatewayIp)
            when (probeResult) {
                is ProbeResult.PortalRedirect -> {
                    val bound = tryBindWifi(network)
                    _state.value = NetworkPortalState(
                        status = WifiPortalStatus.CAPTIVE_PORTAL_DETECTED,
                        gatewayIp = gatewayIp,
                        portalUrl = probeResult.url,
                        interfaceName = interfaceName,
                        detectionSource = probeResult.source,
                        isBoundToWifi = bound,
                        lastCheckedMs = System.currentTimeMillis(),
                    )
                    notifyIfNew(probeResult.url, gatewayIp)
                }

                ProbeResult.InternetWorking -> {
                    val osFlagCaptive = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) == true
                    if (osFlagCaptive && !gatewayIp.isNullOrBlank()) {
                        val fallbackUrl = "http://$gatewayIp/login"
                        val bound = tryBindWifi(network)
                        _state.value = NetworkPortalState(
                            status = WifiPortalStatus.CAPTIVE_PORTAL_DETECTED,
                            gatewayIp = gatewayIp,
                            portalUrl = fallbackUrl,
                            interfaceName = interfaceName,
                            detectionSource = "Android Captive Flag",
                            isBoundToWifi = bound,
                            lastCheckedMs = System.currentTimeMillis(),
                        )
                        notifyIfNew(fallbackUrl, gatewayIp)
                    } else {
                        clearNetworkBinding()
                        _state.value = NetworkPortalState(
                            status = WifiPortalStatus.INTERNET_CONNECTED,
                            gatewayIp = gatewayIp,
                            portalUrl = gatewayIp?.let { "http://$it/status" },
                            interfaceName = interfaceName,
                            detectionSource = "HTTP 204 Validated",
                            isBoundToWifi = false,
                            lastCheckedMs = System.currentTimeMillis(),
                        )
                    }
                }

                ProbeResult.UnknownOrBlocked -> {
                    val osFlagCaptive = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) == true
                    val notValidated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == false
                    if ((osFlagCaptive || notValidated) && !gatewayIp.isNullOrBlank()) {
                        val fallbackUrl = "http://$gatewayIp/login"
                        val bound = tryBindWifi(network)
                        _state.value = NetworkPortalState(
                            status = WifiPortalStatus.CAPTIVE_PORTAL_DETECTED,
                            gatewayIp = gatewayIp,
                            portalUrl = fallbackUrl,
                            interfaceName = interfaceName,
                            detectionSource = "Gateway Fallback ($gatewayIp)",
                            isBoundToWifi = bound,
                            lastCheckedMs = System.currentTimeMillis(),
                        )
                        notifyIfNew(fallbackUrl, gatewayIp)
                    } else {
                        _state.value = NetworkPortalState(
                            status = WifiPortalStatus.INTERNET_CONNECTED,
                            gatewayIp = gatewayIp,
                            portalUrl = gatewayIp?.let { "http://$it/login" },
                            interfaceName = interfaceName,
                            detectionSource = "NetworkCapabilities",
                            isBoundToWifi = false,
                            lastCheckedMs = System.currentTimeMillis(),
                        )
                    }
                }
            }
        }
    }

    private fun tryBindWifi(network: Network): Boolean {
        return try {
            connectivityManager.bindProcessToNetwork(network)
        } catch (_: Exception) {
            false
        }
    }

    private fun notifyIfNew(portalUrl: String, gatewayIp: String?) {
        if (lastNotifiedUrl != portalUrl) {
            lastNotifiedUrl = portalUrl
            onCaptivePortalDetectedCallback?.invoke(portalUrl, gatewayIp)
        }
    }

    private fun extractCaptivePortalApiUrl(linkProperties: LinkProperties?): String? {
        if (linkProperties == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try {
            val method = linkProperties.javaClass.getMethod("getCaptivePortalApiUrl")
            method.invoke(linkProperties)?.toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun extractGatewayIp(linkProperties: LinkProperties?): String? {
        if (linkProperties == null) return null
        for (route in linkProperties.routes) {
            val gw = route.gateway
            if (gw is Inet4Address && !gw.isAnyLocalAddress && route.isDefaultRoute) {
                return gw.hostAddress
            }
        }
        for (route in linkProperties.routes) {
            val gw = route.gateway
            if (gw is Inet4Address && !gw.isAnyLocalAddress) {
                return gw.hostAddress
            }
        }
        return linkProperties.dhcpServerAddress?.hostAddress
    }

    private fun fetchRfc8908PortalUrl(network: Network, apiUrl: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (network.openConnection(URL(apiUrl)) as HttpURLConnection).apply {
                connectTimeout = 2000
                readTimeout = 2000
                requestMethod = "GET"
            }
            if (conn.responseCode in 200..299) {
                val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val json = JSONObject(body)
                val isCaptive = json.optBoolean("captive", false)
                val portalUrl = json.optString("user-portal-url", "")
                if (isCaptive && portalUrl.isNotBlank()) portalUrl else null
            } else null
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private sealed interface ProbeResult {
        data object InternetWorking : ProbeResult
        data class PortalRedirect(val url: String, val source: String) : ProbeResult
        data object UnknownOrBlocked : ProbeResult
    }

    private fun probePortalUrl(network: Network, gatewayIp: String?): ProbeResult {
        val probeTargets = listOf(
            "http://connectivitycheck.gstatic.com/generate_204",
            "http://clients3.google.com/generate_204",
        )

        for (target in probeTargets) {
            var conn: HttpURLConnection? = null
            try {
                conn = (network.openConnection(URL(target)) as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 2200
                    readTimeout = 2200
                    useCaches = false
                    requestMethod = "GET"
                }
                val code = conn.responseCode
                if (code == 204) {
                    return ProbeResult.InternetWorking
                }
                if (code in listOf(301, 302, 303, 307, 308)) {
                    val location = conn.getHeaderField("Location")
                    if (!location.isNullOrBlank()) {
                        return ProbeResult.PortalRedirect(location, "HTTP $code Redirect")
                    }
                }
                if (code == 200) {
                    val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                    val extracted = extractHtmlRedirectUrl(body)
                    if (!extracted.isNullOrBlank()) {
                        return ProbeResult.PortalRedirect(extracted, "MikroTik HTML Redirect")
                    }
                    if (!gatewayIp.isNullOrBlank()) {
                        return ProbeResult.PortalRedirect("http://$gatewayIp/login", "HTTP 200 Intercept")
                    }
                }
            } catch (_: Exception) {
            } finally {
                conn?.disconnect()
            }
        }

        // Last quick check directly against gateway IP if reachable
        if (!gatewayIp.isNullOrBlank()) {
            var gwConn: HttpURLConnection? = null
            try {
                val gwUrl = "http://$gatewayIp/login"
                gwConn = (network.openConnection(URL(gwUrl)) as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 1500
                    readTimeout = 1500
                    requestMethod = "GET"
                }
                val code = gwConn.responseCode
                if (code in 200..399) {
                    return ProbeResult.PortalRedirect(gwConn.url?.toString() ?: gwUrl, "Gateway Direct Probe")
                }
            } catch (_: Exception) {
            } finally {
                gwConn?.disconnect()
            }
        }

        return ProbeResult.UnknownOrBlocked
    }

    companion object {
        fun extractHtmlRedirectUrl(html: String): String? {
            val metaRegex = Regex(
                """<meta[^>]+http-equiv\s*=\s*["']?refresh["']?[^>]+content\s*=\s*["'][^"']*url\s*=\s*([^"'>\s]+)["']?""",
                RegexOption.IGNORE_CASE,
            )
            metaRegex.find(html)?.groupValues?.getOrNull(1)?.let { return it }

            val jsRegex = Regex(
                """(?:window\.location|document\.location|location\.href)\s*=\s*["'](https?://[^"']+)["']""",
                RegexOption.IGNORE_CASE,
            )
            jsRegex.find(html)?.groupValues?.getOrNull(1)?.let { return it }
            return null
        }
    }
}
