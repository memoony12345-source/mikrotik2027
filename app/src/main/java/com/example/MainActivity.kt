package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.MikrotikViewModel
import com.example.ui.UiEvent
import com.example.ui.navigation.HistoryRoute
import com.example.ui.navigation.PortalRoute
import com.example.ui.navigation.RadarRoute
import com.example.ui.navigation.ScannerRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.PortalScreen
import com.example.ui.screens.RadarScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MikrotikTheme
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private var viewModelRef: MikrotikViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MikrotikViewModel = viewModel()
            viewModelRef = viewModel

            LaunchedEffect(Unit) {
                viewModel.handleLaunchIntent(intent)
            }

            MikrotikTheme {
                MikrotikRootApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModelRef?.handleLaunchIntent(intent)
    }
}

private data class TopNavItem<T : Any>(
    val route: T,
    val labelRes: Int,
    val icon: ImageVector,
    val tag: String,
)

@Composable
private fun MikrotikRootApp(viewModel: MikrotikViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val networkState by viewModel.networkState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val portalUrlToLoad by viewModel.portalUrlToLoad.collectAsStateWithLifecycle()
    val portalLoadNonce by viewModel.portalLoadNonce.collectAsStateWithLifecycle()
    val domState by viewModel.domState.collectAsStateWithLifecycle()
    val pendingCode by viewModel.pendingCode.collectAsStateWithLifecycle()
    val lastRecordedCode by viewModel.lastRecordedCode.collectAsStateWithLifecycle()
    val galleryCandidates by viewModel.galleryCandidates.collectAsStateWithLifecycle()
    val isProcessingGallery by viewModel.isProcessingGallery.collectAsStateWithLifecycle()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            viewModel.processGalleryImage(uri)
        }
    }

    fun launchGalleryPicker() {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    fun navigateTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                UiEvent.NavigateToPortal -> navigateTopLevel(PortalRoute)
                UiEvent.NavigateToScanner -> navController.navigate(ScannerRoute)
                UiEvent.OpenGalleryPicker -> launchGalleryPicker()
                is UiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    val navItems = remember {
        listOf(
            TopNavItem(RadarRoute, R.string.nav_radar, Icons.Rounded.Radar, "nav_radar"),
            TopNavItem(PortalRoute, R.string.nav_portal, Icons.Rounded.Language, "nav_portal"),
            TopNavItem(HistoryRoute, R.string.nav_history, Icons.Rounded.History, "nav_history"),
            TopNavItem(SettingsRoute, R.string.nav_settings, Icons.Rounded.Settings, "nav_settings"),
        )
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val isScannerOpen = currentDestination?.hasRoute<ScannerRoute>() == true

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 640.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = if (isScannerOpen) WindowInsets(0) else WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail && !isScannerOpen) {
                    NavigationBar {
                        navItems.forEach { item ->
                            val selected = currentDestination?.hasRoute(item.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = stringResource(item.labelRes),
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.tag),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useRail && !isScannerOpen) {
                    NavigationRail {
                        navItems.forEach { item ->
                            val selected = currentDestination?.hasRoute(item.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = stringResource(item.labelRes),
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.tag),
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = RadarRoute,
                    ) {
                        composable<RadarRoute> {
                            RadarScreen(
                                networkState = networkState,
                                settings = settings,
                                onRefreshNetwork = viewModel::refreshNetwork,
                                onOpenPortal = { viewModel.openDetectedOrDefaultPortal() },
                                onOpenScanner = { navController.navigate(ScannerRoute) },
                                onPickGalleryImage = ::launchGalleryPicker,
                                onOpenTestPortal = viewModel::openTestPortal,
                                onToggleBackgroundMonitor = viewModel::setBackgroundMonitor,
                            )
                        }

                        composable<PortalRoute> {
                            PortalScreen(
                                portalUrlToLoad = portalUrlToLoad,
                                portalLoadNonce = portalLoadNonce,
                                domState = domState,
                                networkState = networkState,
                                pendingCode = pendingCode,
                                lastRecordedCode = lastRecordedCode,
                                galleryCandidates = galleryCandidates,
                                isProcessingGallery = isProcessingGallery,
                                onLoadUrl = viewModel::openDetectedOrDefaultPortal,
                                onPageLoadingChanged = viewModel::updatePageLoading,
                                onDomStateChanged = viewModel::updateDomState,
                                onOpenScanner = { navController.navigate(ScannerRoute) },
                                onPickGalleryImage = ::launchGalleryPicker,
                                onSubmitVoucherCode = viewModel::submitVoucherCode,
                                onCodeInjected = viewModel::clearPendingCode,
                                onDismissGalleryCandidates = viewModel::dismissGalleryCandidates,
                                onOpenTestPortal = viewModel::openTestPortal,
                            )
                        }

                        composable<ScannerRoute> {
                            ScannerScreen(
                                codeMode = settings.codeMode,
                                onCodeConfirmed = { code ->
                                    viewModel.submitVoucherCode(code, "CAMERA")
                                    navController.popBackStack()
                                    navigateTopLevel(PortalRoute)
                                },
                                onPickFromGallery = {
                                    navController.popBackStack()
                                    launchGalleryPicker()
                                },
                                onBack = { navController.popBackStack() },
                            )
                        }

                        composable<HistoryRoute> {
                            HistoryScreen(
                                history = history,
                                profiles = profiles,
                                onUseVoucherInPortal = { code, url ->
                                    if (!url.isNullOrBlank()) {
                                        viewModel.openDetectedOrDefaultPortal(url)
                                    } else {
                                        navigateTopLevel(PortalRoute)
                                    }
                                    viewModel.submitVoucherCode(code, "MANUAL")
                                },
                                onOpenSavedPortal = { url ->
                                    viewModel.openDetectedOrDefaultPortal(url)
                                },
                                onDeleteHistoryItem = viewModel::deleteHistoryItem,
                                onClearHistory = viewModel::clearHistory,
                                onDeleteProfile = viewModel::deleteProfile,
                            )
                        }

                        composable<SettingsRoute> {
                            SettingsScreen(
                                settings = settings,
                                onToggleBackgroundMonitor = viewModel::setBackgroundMonitor,
                                onToggleAutoOpen = viewModel::setAutoOpenPortal,
                                onToggleAutoSubmit = viewModel::setAutoSubmit,
                                onToggleMirrorPassword = viewModel::setMirrorPassword,
                                onSelectCodeMode = viewModel::setCodeMode,
                            )
                        }
                    }
                }
            }
        }
    }
}
