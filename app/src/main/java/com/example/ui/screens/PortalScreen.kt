package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.data.model.NetworkPortalState
import com.example.data.model.PortalDomState
import com.example.data.model.VoucherCandidate
import com.example.portal.MikroTikJsInterface
import com.example.portal.MikroTikWebBridge
import com.example.ui.PendingCodeInjection
import com.example.ui.theme.JetBrainsMonoFontFamily

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PortalScreen(
    portalUrlToLoad: String?,
    portalLoadNonce: Long,
    domState: PortalDomState,
    networkState: NetworkPortalState,
    pendingCode: PendingCodeInjection?,
    lastRecordedCode: String?,
    galleryCandidates: List<VoucherCandidate>,
    isProcessingGallery: Boolean,
    onLoadUrl: (String) -> Unit,
    onPageLoadingChanged: (String, Boolean) -> Unit,
    onDomStateChanged: (String, String, Boolean, Boolean, Boolean, String, Boolean) -> Unit,
    onOpenScanner: () -> Unit,
    onPickGalleryImage: () -> Unit,
    onSubmitVoucherCode: (String, String) -> Unit,
    onCodeInjected: () -> Unit,
    onDismissGalleryCandidates: () -> Unit,
    onOpenTestPortal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var addressText by remember(portalUrlToLoad) {
        mutableStateOf(portalUrlToLoad ?: MikroTikWebBridge.TEST_PORTAL_URL)
    }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var showManualDialog by remember { mutableStateOf(false) }
    var manualInput by remember { mutableStateOf("") }

    // Load URL when requested or when WebView is first attached
    LaunchedEffect(webViewRef, portalUrlToLoad, portalLoadNonce) {
        val webView = webViewRef ?: return@LaunchedEffect
        val target = portalUrlToLoad ?: MikroTikWebBridge.TEST_PORTAL_URL
        if (target == MikroTikWebBridge.TEST_PORTAL_URL) {
            webView.loadDataWithBaseURL(
                MikroTikWebBridge.TEST_PORTAL_URL,
                MikroTikWebBridge.TEST_PORTAL_HTML,
                "text/html",
                "UTF-8",
                null,
            )
        } else {
            webView.loadUrl(target)
        }
    }

    // Inject scanned/selected voucher code as soon as page & voucher field are ready
    LaunchedEffect(pendingCode, domState.hasVoucherField, domState.isLoading) {
        val injection = pendingCode ?: return@LaunchedEffect
        val webView = webViewRef ?: return@LaunchedEffect
        if (!domState.isLoading && domState.hasVoucherField) {
            val script = MikroTikWebBridge.buildFillAndSubmitScript(
                code = injection.code,
                mirrorPassword = injection.mirrorPassword,
                autoSubmit = injection.autoSubmit,
            )
            webView.evaluateJavascript(script) {
                onCodeInjected()
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Address & Quick Gateway Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        OutlinedTextField(
                            value = addressText,
                            onValueChange = { addressText = it },
                            singleLine = true,
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.portal_url_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            textStyle = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                            ),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go,
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = { onLoadUrl(addressText) },
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("portal_url_input"),
                        )
                    }

                    FilledTonalButton(
                        onClick = { onLoadUrl(addressText) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("portal_go_button"),
                    ) {
                        Text(text = stringResource(R.string.portal_go))
                    }

                    IconButton(
                        onClick = { webViewRef?.reload() },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("portal_reload_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "تحديث الصفحة",
                        )
                    }
                }

                // Quick MikroTik Gateway Shortcuts
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    networkState.portalUrl?.let { detected ->
                        AssistChip(
                            onClick = {
                                addressText = detected
                                onLoadUrl(detected)
                            },
                            label = { Text("البوابة المكتشفة") },
                        )
                    }
                    networkState.gatewayIp?.let { gw ->
                        val gwUrl = "http://$gw/login"
                        AssistChip(
                            onClick = {
                                addressText = gwUrl
                                onLoadUrl(gwUrl)
                            },
                            label = { Text(gw) },
                        )
                    }
                    AssistChip(
                        onClick = {
                            addressText = MikroTikWebBridge.TEST_PORTAL_URL
                            onOpenTestPortal()
                        },
                        label = { Text("بوابة تجريبية") },
                    )
                    AssistChip(
                        onClick = {
                            addressText = "http://10.5.50.1/login"
                            onLoadUrl("http://10.5.50.1/login")
                        },
                        label = { Text("10.5.50.1") },
                    )
                    AssistChip(
                        onClick = {
                            addressText = "http://192.168.88.1/login"
                            onLoadUrl("http://192.168.88.1/login")
                        },
                        label = { Text("192.168.88.1") },
                    )
                }
            }
        }

        if (domState.isLoading || isProcessingGallery) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        // WebView + Floating Smart Voucher Prompt
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false

                        addJavascriptInterface(
                            MikroTikJsInterface { url, title, hasVoucher, hasPwd, hasBtn, hint, isStatus ->
                                post {
                                    onDomStateChanged(
                                        url,
                                        title,
                                        hasVoucher,
                                        hasPwd,
                                        hasBtn,
                                        hint,
                                        isStatus,
                                    )
                                }
                            },
                            "MikrotikBridge",
                        )

                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean {
                                val u = request?.url?.toString() ?: return false
                                if (u.startsWith("http://mikrotik-test.local")) {
                                    view?.loadDataWithBaseURL(
                                        MikroTikWebBridge.TEST_PORTAL_URL,
                                        MikroTikWebBridge.TEST_PORTAL_HTML,
                                        "text/html",
                                        "UTF-8",
                                        null,
                                    )
                                    return true
                                }
                                return false
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                val u = url ?: ""
                                if (u.isNotBlank()) addressText = u
                                onPageLoadingChanged(u, true)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                val u = url ?: ""
                                onPageLoadingChanged(u, false)
                                view?.evaluateJavascript(MikroTikWebBridge.DOM_INSPECTOR_SCRIPT, null)
                            }
                        }

                        webViewRef = this
                    }
                },
            )

            // Gallery candidate selector sheet if multiple codes found in picked photo
            if (galleryCandidates.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(12.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "اختر كود الكرت من الصورة:",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            IconButton(
                                onClick = onDismissGalleryCandidates,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "إغلاق")
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            galleryCandidates.forEach { candidate ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier
                                        .clickable {
                                            onSubmitVoucherCode(candidate.code, "GALLERY")
                                        },
                                ) {
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                        Text(
                                            text = candidate.code,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontFamily = JetBrainsMonoFontFamily,
                                            ),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Floating Smart Prompt Banner when Voucher Field is Detected on the Portal
            androidx.compose.animation.AnimatedVisibility(
                visible = domState.hasVoucherField || domState.isStatusOrConnectedPage,
                enter = fadeIn() + slideInVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
            ) {
                if (domState.isStatusOrConnectedPage) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                            Text(
                                text = stringResource(R.string.portal_logged_in),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                } else {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                        ),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoFixHigh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.portal_field_detected),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (domState.detectedFieldHint.isNotBlank()) {
                                        Text(
                                            text = "الحقل: ${domState.detectedFieldHint}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        manualInput = lastRecordedCode.orEmpty()
                                        showManualDialog = true
                                    },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .testTag("manual_voucher_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = stringResource(R.string.portal_manual_code),
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = onOpenScanner,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("portal_scan_camera_button"),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CameraAlt,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "مسح بالكاميرا",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }

                                OutlinedButton(
                                    onClick = onPickGalleryImage,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("portal_pick_gallery_button"),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PhotoLibrary,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "من الاستوديو",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            if (!lastRecordedCode.isNullOrBlank()) {
                                TextButton(
                                    onClick = { onSubmitVoucherCode(lastRecordedCode, "MANUAL") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentPaste,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("تعبئة آخر كود: $lastRecordedCode")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showManualDialog) {
        AlertDialog(
            onDismissRequest = { showManualDialog = false },
            title = { Text(stringResource(R.string.portal_manual_code)) },
            text = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(
                        value = manualInput,
                        onValueChange = { next ->
                            if (next.length <= 12) manualInput = next.filter { it.isLetterOrDigit() }
                        },
                        singleLine = true,
                        label = { Text("6 - 12 characters") },
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_code_dialog_input"),
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = manualInput.length in 6..12,
                    onClick = {
                        showManualDialog = false
                        onSubmitVoucherCode(manualInput, "MANUAL")
                    },
                    modifier = Modifier.testTag("manual_code_confirm_button"),
                ) {
                    Text(stringResource(R.string.portal_submit_now))
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualDialog = false }) {
                    Text("إلغاء")
                }
            },
        )
    }
}
