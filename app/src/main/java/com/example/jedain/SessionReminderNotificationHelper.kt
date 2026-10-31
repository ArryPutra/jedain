package com.example.jedain

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log

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
        // Sistem notifikasi pengingat sesi lama telah digantikan oleh Floating Snackbar Reminder.
        // Fungsi ini dinonaktifkan agar tidak ada notifikasi pengingat ganda.
        Log.d(TAG, "Legacy 2-minute notification disabled; replaced by Floating Snackbar Reminder")
    }

    fun send30SecondWarning(context: Context) {
        // Sistem notifikasi pengingat sesi lama telah digantikan oleh Floating Snackbar Reminder.
        // Fungsi ini dinonaktifkan agar tidak ada notifikasi pengingat ganda.
        Log.d(TAG, "Legacy 30-second notification disabled; replaced by Floating Snackbar Reminder")
    }

    fun cancelAllReminders(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        manager.cancel(NOTIF_ID_2MIN)
        manager.cancel(NOTIF_ID_30SEC)
        Log.d(TAG, "Cancelled session reminder notifications ($NOTIF_ID_2MIN, $NOTIF_ID_30SEC)")
    }
}
