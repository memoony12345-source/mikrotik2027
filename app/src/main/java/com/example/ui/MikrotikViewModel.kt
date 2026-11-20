package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MikrotikApp
import com.example.data.local.PortalProfileEntity
import com.example.data.local.VoucherHistoryEntity
import com.example.data.model.AppSettings
import com.example.data.model.NetworkPortalState
import com.example.data.model.PortalDomState
import com.example.data.model.VoucherCandidate
import com.example.data.model.VoucherCodeMode
import com.example.data.model.WifiPortalStatus
import com.example.network.PortalNotificationHelper
import com.example.network.PortalWatchService
import com.example.ocr.OcrHelper
import com.example.portal.MikroTikWebBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PendingCodeInjection(
    val code: String,
    val source: String,
    val mirrorPassword: Boolean,
    val autoSubmit: Boolean,
    val id: Long = System.nanoTime(),
)

sealed interface UiEvent {
    data object NavigateToPortal : UiEvent
    data object NavigateToScanner : UiEvent
    data object OpenGalleryPicker : UiEvent
    data class ShowMessage(val message: String) : UiEvent
}

class MikrotikViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MikrotikApp
    private val networkMonitor = app.networkMonitor
    private val mikrotikRepo = app.mikrotikRepository
    private val settingsRepo = app.settingsRepository

    val networkState: StateFlow<NetworkPortalState> = networkMonitor.state

    val settings: StateFlow<AppSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val history: StateFlow<List<VoucherHistoryEntity>> = mikrotikRepo.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val profiles: StateFlow<List<PortalProfileEntity>> = mikrotikRepo.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _portalUrlToLoad = MutableStateFlow<String?>(null)
    val portalUrlToLoad: StateFlow<String?> = _portalUrlToLoad.asStateFlow()

    private val _portalLoadNonce = MutableStateFlow(0L)
    val portalLoadNonce: StateFlow<Long> = _portalLoadNonce.asStateFlow()

    private val _domState = MutableStateFlow(PortalDomState())
    val domState: StateFlow<PortalDomState> = _domState.asStateFlow()

    private val _pendingCode = MutableStateFlow<PendingCodeInjection?>(null)
    val pendingCode: StateFlow<PendingCodeInjection?> = _pendingCode.asStateFlow()

    private val _galleryCandidates = MutableStateFlow<List<VoucherCandidate>>(emptyList())
    val galleryCandidates: StateFlow<List<VoucherCandidate>> = _galleryCandidates.asStateFlow()

    private val _isProcessingGallery = MutableStateFlow(false)
    val isProcessingGallery: StateFlow<Boolean> = _isProcessingGallery.asStateFlow()

    private val _lastRecordedCode = MutableStateFlow<String?>(null)
    val lastRecordedCode: StateFlow<String?> = _lastRecordedCode.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private var lastAutoOpenedUrl: String? = null
    private var lastPromptNotifiedUrl: String? = null

    init {
        viewModelScope.launch {
            combine(networkState, settings) { net, cfg -> net to cfg }
                .collect { (net, cfg) ->
                    if (net.status == WifiPortalStatus.CAPTIVE_PORTAL_DETECTED && !net.portalUrl.isNullOrBlank()) {
                        if (_portalUrlToLoad.value.isNullOrBlank()) {
                            _portalUrlToLoad.value = net.portalUrl
                            _portalLoadNonce.value = System.nanoTime()
                        }
                        if (cfg.autoOpenPortalOnDetect && lastAutoOpenedUrl != net.portalUrl) {
                            lastAutoOpenedUrl = net.portalUrl
                            _portalUrlToLoad.value = net.portalUrl
                            _portalLoadNonce.value = System.nanoTime()
                            _events.tryEmit(UiEvent.NavigateToPortal)
                        }
                    }
                }
        }
    }

    fun handleLaunchIntent(intent: Intent?) {
        if (intent == null) return
        val target = intent.getStringExtra(PortalNotificationHelper.EXTRA_NAV_TARGET) ?: return
        val url = intent.getStringExtra(PortalNotificationHelper.EXTRA_PORTAL_URL)
        if (!url.isNullOrBlank()) {
            _portalUrlToLoad.value = url
            _portalLoadNonce.value = System.nanoTime()
        }
        when (target) {
            PortalNotificationHelper.TARGET_PORTAL -> _events.tryEmit(UiEvent.NavigateToPortal)
            PortalNotificationHelper.TARGET_SCANNER -> _events.tryEmit(UiEvent.NavigateToScanner)
            PortalNotificationHelper.TARGET_GALLERY -> {
                _events.tryEmit(UiEvent.NavigateToPortal)
                _events.tryEmit(UiEvent.OpenGalleryPicker)
            }
        }
        intent.removeExtra(PortalNotificationHelper.EXTRA_NAV_TARGET)
    }

    fun refreshNetwork() {
        networkMonitor.refreshNow()
    }

    fun openDetectedOrDefaultPortal(customUrl: String? = null) {
        val chosen = customUrl?.trim()?.takeIf { it.isNotBlank() }
            ?: networkState.value.portalUrl
            ?: networkState.value.gatewayIp?.let { "http://$it/login" }
            ?: MikroTikWebBridge.TEST_PORTAL_URL

        val normalized = if (chosen.startsWith("http://") || chosen.startsWith("https://")) {
            chosen
        } else {
            "http://$chosen"
        }

        if (normalized != MikroTikWebBridge.TEST_PORTAL_URL) {
            networkMonitor.bindToWifiNetwork()
        }
        lastPromptNotifiedUrl = null
        _portalUrlToLoad.value = normalized
        _portalLoadNonce.value = System.nanoTime()
        _events.tryEmit(UiEvent.NavigateToPortal)
    }

    fun openTestPortal() {
        lastPromptNotifiedUrl = null
        _portalUrlToLoad.value = MikroTikWebBridge.TEST_PORTAL_URL
        _portalLoadNonce.value = System.nanoTime()
        _events.tryEmit(UiEvent.NavigateToPortal)
    }

    fun updatePageLoading(url: String, isLoading: Boolean) {
        _domState.value = _domState.value.copy(
            currentUrl = url,
            isLoading = isLoading,
            isTestPortalMode = url.startsWith("http://mikrotik-test.local"),
        )
    }

    fun updateDomState(
        url: String,
        title: String,
        hasVoucherField: Boolean,
        hasPasswordField: Boolean,
        hasSubmitButton: Boolean,
        fieldHint: String,
        isStatusPage: Boolean,
    ) {
        val isTest = url.startsWith("http://mikrotik-test.local")
        _domState.value = PortalDomState(
            currentUrl = url,
            pageTitle = title,
            hasVoucherField = hasVoucherField,
            hasPasswordField = hasPasswordField,
            hasSubmitButton = hasSubmitButton,
            detectedFieldHint = fieldHint,
            isStatusOrConnectedPage = isStatusPage,
            isLoading = false,
            isTestPortalMode = isTest,
        )

        if (hasVoucherField && lastPromptNotifiedUrl != url) {
            lastPromptNotifiedUrl = url
            PortalNotificationHelper.showVoucherFieldReadyNotification(app, url)
        }
    }

    fun submitVoucherCode(code: String, source: String) {
        val cleanCode = code.trim()
        if (cleanCode.length !in 6..12) {
            _events.tryEmit(UiEvent.ShowMessage("طول الكود يجب أن يكون من 6 إلى 12 خانة"))
            return
        }

        val cfg = settings.value
        _lastRecordedCode.value = cleanCode
        _galleryCandidates.value = emptyList()

        if (_portalUrlToLoad.value.isNullOrBlank()) {
            val detected = networkState.value.portalUrl
                ?: networkState.value.gatewayIp?.let { "http://$it/login" }
                ?: MikroTikWebBridge.TEST_PORTAL_URL
            _portalUrlToLoad.value = detected
            _portalLoadNonce.value = System.nanoTime()
        }

        _pendingCode.value = PendingCodeInjection(
            code = cleanCode,
            source = source,
            mirrorPassword = cfg.mirrorCodeToPassword,
            autoSubmit = cfg.autoSubmitAfterScan,
        )

        viewModelScope.launch {
            val currentUrl = _domState.value.currentUrl.ifBlank { _portalUrlToLoad.value.orEmpty() }
            val host = extractHost(currentUrl).ifBlank { networkState.value.gatewayIp ?: "hotspot" }
            val historyId = mikrotikRepo.recordVoucherUse(
                code = cleanCode,
                portalHost = host,
                portalUrl = currentUrl,
                source = source,
                verifiedConnected = false,
            )

            if (!_domState.value.isTestPortalMode && cfg.autoSubmitAfterScan) {
                delay(2200)
                val verified = networkMonitor.verifyInternetAfterLogin()
                if (verified) {
                    mikrotikRepo.markVoucherConnected(historyId)
                    _events.tryEmit(UiEvent.ShowMessage("تم تسجيل الدخول والاتصال بالإنترنت بنجاح!"))
                }
            }
        }
    }

    fun clearPendingCode() {
        _pendingCode.value = null
    }

    fun processGalleryImage(uri: Uri) {
        viewModelScope.launch {
            _isProcessingGallery.value = true
            _galleryCandidates.value = emptyList()
            try {
                val results = OcrHelper.recognizeFromUri(app, uri, settings.value.codeMode)
                if (results.isEmpty()) {
                    _events.tryEmit(
                        UiEvent.ShowMessage("لم يتم العثور على كود كرت (6-12 خانة) داخل الصورة"),
                    )
                } else if (results.size == 1 && results.first().confidenceScore >= 0.78f) {
                    submitVoucherCode(results.first().code, "GALLERY")
                    _events.tryEmit(UiEvent.NavigateToPortal)
                } else {
                    _galleryCandidates.value = results
                    _events.tryEmit(UiEvent.NavigateToPortal)
                }
            } catch (_: Exception) {
                _events.tryEmit(UiEvent.ShowMessage("تعذر تحليل الصورة المختارة، حاول مرة أخرى"))
            } finally {
                _isProcessingGallery.value = false
            }
        }
    }

    fun dismissGalleryCandidates() {
        _galleryCandidates.value = emptyList()
    }

    fun setBackgroundMonitor(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setBackgroundMonitor(enabled)
            if (enabled) {
                PortalWatchService.start(app)
            } else {
                PortalWatchService.stop(app)
            }
        }
    }

    fun setAutoOpenPortal(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setAutoOpenPortal(enabled) }
    }

    fun setAutoSubmit(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setAutoSubmit(enabled) }
    }

    fun setMirrorPassword(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setMirrorPassword(enabled) }
    }

    fun setCodeMode(mode: VoucherCodeMode) {
        viewModelScope.launch { settingsRepo.setCodeMode(mode) }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch { mikrotikRepo.deleteHistoryItem(id) }
    }

    fun clearHistory() {
        viewModelScope.launch { mikrotikRepo.clearHistory() }
    }

    fun deleteProfile(host: String) {
        viewModelScope.launch { mikrotikRepo.deletePortalProfile(host) }
    }

    private fun extractHost(url: String): String {
        return try {
            Uri.parse(url).host.orEmpty()
        } catch (_: Exception) {
            ""
        }
    }
}
