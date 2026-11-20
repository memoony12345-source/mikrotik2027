package com.example.data.model

enum class VoucherCodeMode(val labelAr: String) {
    AUTO("تلقائي (أرقام أو حروف وأرقام)"),
    DIGITS_ONLY("أرقام فقط (6-12 رقم)"),
    ALPHANUMERIC("حروف إنجليزية وأرقام"),
}

enum class WifiPortalStatus {
    NO_WIFI,
    CHECKING,
    CAPTIVE_PORTAL_DETECTED,
    INTERNET_CONNECTED,
}

data class NetworkPortalState(
    val status: WifiPortalStatus = WifiPortalStatus.NO_WIFI,
    val gatewayIp: String? = null,
    val portalUrl: String? = null,
    val interfaceName: String? = null,
    val detectionSource: String? = null,
    val isBoundToWifi: Boolean = false,
    val lastCheckedMs: Long = 0L,
)

data class PortalDomState(
    val currentUrl: String = "",
    val pageTitle: String = "",
    val hasVoucherField: Boolean = false,
    val hasPasswordField: Boolean = false,
    val hasSubmitButton: Boolean = false,
    val detectedFieldHint: String = "",
    val isStatusOrConnectedPage: Boolean = false,
    val isLoading: Boolean = false,
    val isTestPortalMode: Boolean = false,
)

data class VoucherCandidate(
    val code: String,
    val confidenceScore: Float,
    val isDigitsOnly: Boolean,
    val inRoi: Boolean,
)

data class AppSettings(
    val backgroundMonitorEnabled: Boolean = false,
    val autoOpenPortalOnDetect: Boolean = true,
    val autoSubmitAfterScan: Boolean = true,
    val mirrorCodeToPassword: Boolean = true,
    val codeMode: VoucherCodeMode = VoucherCodeMode.AUTO,
)
