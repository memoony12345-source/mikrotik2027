package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AppSettings
import com.example.data.model.VoucherCodeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mikrotik_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val BG_MONITOR = booleanPreferencesKey("bg_monitor_enabled")
        val AUTO_OPEN_PORTAL = booleanPreferencesKey("auto_open_portal")
        val AUTO_SUBMIT = booleanPreferencesKey("auto_submit_after_scan")
        val MIRROR_PASSWORD = booleanPreferencesKey("mirror_password")
        val CODE_MODE = stringPreferencesKey("code_mode")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val modeName = prefs[Keys.CODE_MODE] ?: VoucherCodeMode.AUTO.name
        val mode = VoucherCodeMode.entries.firstOrNull { it.name == modeName } ?: VoucherCodeMode.AUTO
        AppSettings(
            backgroundMonitorEnabled = prefs[Keys.BG_MONITOR] ?: false,
            autoOpenPortalOnDetect = prefs[Keys.AUTO_OPEN_PORTAL] ?: true,
            autoSubmitAfterScan = prefs[Keys.AUTO_SUBMIT] ?: true,
            mirrorCodeToPassword = prefs[Keys.MIRROR_PASSWORD] ?: true,
            codeMode = mode,
        )
    }

    suspend fun setBackgroundMonitor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BG_MONITOR] = enabled }
    }

    suspend fun setAutoOpenPortal(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_OPEN_PORTAL] = enabled }
    }

    suspend fun setAutoSubmit(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_SUBMIT] = enabled }
    }

    suspend fun setMirrorPassword(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MIRROR_PASSWORD] = enabled }
    }

    suspend fun setCodeMode(mode: VoucherCodeMode) {
        context.dataStore.edit { it[Keys.CODE_MODE] = mode.name }
    }
}
