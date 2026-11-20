package com.example.network

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.example.MikrotikApp

class PortalWatchService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = PortalNotificationHelper.buildMonitorForegroundNotification(
            this,
            "جاري مراقبة شبكات المايكروتك تلقائياً",
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                PortalNotificationHelper.MONITOR_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(PortalNotificationHelper.MONITOR_NOTIFICATION_ID, notification)
        }

        val monitor = (application as MikrotikApp).networkMonitor
        monitor.onCaptivePortalDetectedCallback = { portalUrl, gatewayIp ->
            PortalNotificationHelper.showCaptivePortalDetectedNotification(
                applicationContext,
                portalUrl,
                gatewayIp,
            )
        }
        monitor.startMonitoring()

        return START_STICKY
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, PortalWatchService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PortalWatchService::class.java))
        }
    }
}
