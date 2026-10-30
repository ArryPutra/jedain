package com.example.jedain

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat

object SessionReminderNotificationHelper {

    private const val TAG = "JedaInReminder"

    const val LEGACY_CHANNEL_ID = "jedain_session_reminder_channel"
    const val CHANNEL_ID = "jedain_session_reminder_channel_v2"
    const val CHANNEL_NAME = "Peringatan Sesi Jeda"
    const val CHANNEL_DESCRIPTION = "Pengingat ketika waktu sesi penggunaan aplikasi hampir berakhir."

    const val NOTIF_ID_2MIN = 2001
    const val NOTIF_ID_30SEC = 2002

    fun createNotificationChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        // Migrasi channel lama jika ada untuk memastikan IMPORTANCE_HIGH aktif
        runCatching {
            val legacyChannel = manager.getNotificationChannel(LEGACY_CHANNEL_ID)
            if (legacyChannel != null) {
                manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
                Log.d(TAG, "Deleted legacy notification channel: $LEGACY_CHANNEL_ID")
            }
        }

        // Buat channel baru dengan IMPORTANCE_HIGH agar Heads-up popup banner dapat muncul
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = CHANNEL_DESCRIPTION
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
        Log.d(TAG, "NotificationChannel '$CHANNEL_NAME' initialized with IMPORTANCE_HIGH")
    }

    private fun getPendingIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, requestCode, intent, flags)
    }

    fun send2MinuteWarning(context: Context) {
        if (!PermissionHelper.hasNotificationPermission(context)) {
            Log.w(TAG, "POST_NOTIFICATIONS permission not granted, skipping 2-minute warning")
            return
        }

        createNotificationChannel(context)

        // Konfigurasi Heads-up Notification: PRIORITY_HIGH + DEFAULT_ALL + CATEGORY_REMINDER
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_jedain)
            .setContentTitle("Waktu sesi hampir habis")
            .setContentText("Sesi kamu tersisa 2 menit. Bersiaplah untuk mengambil jeda sejenak.")
            .setContentIntent(getPendingIntent(context, 101))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIF_ID_2MIN, notification)
        Log.d(TAG, "Sent 2-minute heads-up warning notification (id=$NOTIF_ID_2MIN, channel=$CHANNEL_ID)")
    }

    fun send30SecondWarning(context: Context) {
        if (!PermissionHelper.hasNotificationPermission(context)) {
            Log.w(TAG, "POST_NOTIFICATIONS permission not granted, skipping 30-second warning")
            return
        }

        createNotificationChannel(context)

        // Konfigurasi Heads-up Notification: PRIORITY_HIGH + DEFAULT_ALL + CATEGORY_REMINDER
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_jedain)
            .setContentTitle("Sebentar lagi waktunya berhenti")
            .setContentText("Sesi kamu akan segera berakhir. Bersiaplah menentukan langkah berikutnya.")
            .setContentIntent(getPendingIntent(context, 102))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIF_ID_30SEC, notification)
        Log.d(TAG, "Sent 30-second heads-up warning notification (id=$NOTIF_ID_30SEC, channel=$CHANNEL_ID)")
    }

    fun cancelAllReminders(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        manager.cancel(NOTIF_ID_2MIN)
        manager.cancel(NOTIF_ID_30SEC)
        Log.d(TAG, "Cancelled session reminder notifications ($NOTIF_ID_2MIN, $NOTIF_ID_30SEC)")
    }
}
