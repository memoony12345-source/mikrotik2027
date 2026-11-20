package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.local.MikrotikRepository
import com.example.data.local.SettingsRepository
import com.example.network.NetworkPortalMonitor
import com.example.network.PortalNotificationHelper

class MikrotikApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val mikrotikRepository: MikrotikRepository by lazy {
        MikrotikRepository(database.voucherHistoryDao(), database.portalProfileDao())
    }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val networkMonitor: NetworkPortalMonitor by lazy { NetworkPortalMonitor(this) }

    override fun onCreate() {
        super.onCreate()
        PortalNotificationHelper.ensureChannels(this)
        networkMonitor.onCaptivePortalDetectedCallback = { portalUrl, gatewayIp ->
            PortalNotificationHelper.showCaptivePortalDetectedNotification(
                applicationContext,
                portalUrl,
                gatewayIp,
            )
        }
        networkMonitor.startMonitoring()
    }
}
