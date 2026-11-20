package com.example.network

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object PortalNotificationHelper {

    const val CHANNEL_MONITOR_ID = "mikrotik_monitor_channel"
    const val CHANNEL_PORTAL_ID = "mikrotik_portal_alerts"

    const val MONITOR_NOTIFICATION_ID = 202701
    const val PORTAL_ALERT_NOTIFICATION_ID = 202702
    const val SCAN_PROMPT_NOTIFICATION_ID = 202703

    const val EXTRA_NAV_TARGET = "extra_nav_target"
    const val EXTRA_PORTAL_URL = "extra_portal_url"
    const val TARGET_PORTAL = "portal"
    const val TARGET_SCANNER = "scanner"
    const val TARGET_GALLERY = "gallery"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return

            val monitorChannel = NotificationChannel(
                CHANNEL_MONITOR_ID,
                context.getString(R.string.notif_channel_monitor_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notif_channel_monitor_desc)
                setShowBadge(false)
            }

            val portalChannel = NotificationChannel(
                CHANNEL_PORTAL_ID,
                context.getString(R.string.notif_channel_portal_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notif_channel_portal_desc)
                enableVibration(true)
            }

            manager.createNotificationChannel(monitorChannel)
            manager.createNotificationChannel(portalChannel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun buildMonitorForegroundNotification(context: Context, statusText: String): Notification {
        ensureChannels(context)
        val openAppIntent = PendingIntent.getActivity(
            context,
            100,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, CHANNEL_MONITOR_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("mikrotik 2027 • المراقب التلقائي")
            .setContentText(statusText)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppIntent)
            .build()
    }

    fun showCaptivePortalDetectedNotification(
        context: Context,
        portalUrl: String,
        gatewayIp: String?,
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val openPortalIntent = PendingIntent.getActivity(
            context,
            201,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_NAV_TARGET, TARGET_PORTAL)
                putExtra(EXTRA_PORTAL_URL, portalUrl)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val openScannerIntent = PendingIntent.getActivity(
            context,
            202,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_NAV_TARGET, TARGET_SCANNER)
                putExtra(EXTRA_PORTAL_URL, portalUrl)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val hostLabel = gatewayIp?.let { "البوابة $it" } ?: portalUrl
        val notification = NotificationCompat.Builder(context, CHANNEL_PORTAL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("تم اكتشاف بوابة مايكروتك")
            .setContentText("اضغط لفتح صفحة التسجيل أو مسح الكرت بالكاميرا ($hostLabel)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openPortalIntent)
            .addAction(0, "فتح البوابة", openPortalIntent)
            .addAction(0, "مسح الكرت بالكاميرا", openScannerIntent)
            .build()

        NotificationManagerCompat.from(context).notify(PORTAL_ALERT_NOTIFICATION_ID, notification)
    }

    fun showVoucherFieldReadyNotification(
        context: Context,
        portalUrl: String,
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val scanCameraIntent = PendingIntent.getActivity(
            context,
            301,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_NAV_TARGET, TARGET_SCANNER)
                putExtra(EXTRA_PORTAL_URL, portalUrl)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val pickGalleryIntent = PendingIntent.getActivity(
            context,
            302,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_NAV_TARGET, TARGET_GALLERY)
                putExtra(EXTRA_PORTAL_URL, portalUrl)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_PORTAL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("مربع الكرت جاهز للتعبئة")
            .setContentText("امسح كرت المايكروتك بالكاميرا أو اختر صورته من الاستوديو للتسجيل الفوري")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(scanCameraIntent)
            .addAction(0, "الكاميرا", scanCameraIntent)
            .addAction(0, "الاستوديو", pickGalleryIntent)
            .build()

        NotificationManagerCompat.from(context).notify(SCAN_PROMPT_NOTIFICATION_ID, notification)
    }
}
