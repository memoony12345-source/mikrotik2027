package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AppSettings
import com.example.data.model.VoucherCodeMode
import com.example.network.PortalNotificationHelper

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onToggleBackgroundMonitor: (Boolean) -> Unit,
    onToggleAutoOpen: (Boolean) -> Unit,
    onToggleAutoSubmit: (Boolean) -> Unit,
    onToggleMirrorPassword: (Boolean) -> Unit,
    onSelectCodeMode: (VoucherCodeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            onToggleBackgroundMonitor(true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
            )

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingSwitchRow(
                        title = stringResource(R.string.setting_bg_monitor),
                        description = stringResource(R.string.setting_bg_monitor_desc),
                        checked = settings.backgroundMonitorEnabled,
                        testTag = "setting_bg_monitor_switch",
                        onCheckedChange = { enable ->
                            if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !PortalNotificationHelper.hasNotificationPermission(context)
                            ) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                onToggleBackgroundMonitor(enable)
                            }
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    SettingSwitchRow(
                        title = stringResource(R.string.setting_auto_open),
                        description = stringResource(R.string.setting_auto_open_desc),
                        checked = settings.autoOpenPortalOnDetect,
                        testTag = "setting_auto_open_switch",
                        onCheckedChange = onToggleAutoOpen,
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    SettingSwitchRow(
                        title = stringResource(R.string.setting_auto_submit),
                        description = stringResource(R.string.setting_auto_submit_desc),
                        checked = settings.autoSubmitAfterScan,
                        testTag = "setting_auto_submit_switch",
                        onCheckedChange = onToggleAutoSubmit,
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    SettingSwitchRow(
                        title = stringResource(R.string.setting_mirror_password),
                        description = stringResource(R.string.setting_mirror_password_desc),
                        checked = settings.mirrorCodeToPassword,
                        testTag = "setting_mirror_password_switch",
                        onCheckedChange = onToggleMirrorPassword,
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.setting_code_mode),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "يدعم التطبيق أكواد الكروت من 6 إلى 12 خانة فقط (أرقام أو حروف وأرقام إنجليزية).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    VoucherCodeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectCodeMode(mode) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RadioButton(
                                selected = settings.codeMode == mode,
                                onClick = { onSelectCodeMode(mode) },
                            )
                            Text(
                                text = mode.labelAr,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
        )
    }
}
