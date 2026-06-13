package com.rork.whispercell.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.rork.whispercell.MainActivity
import com.rork.whispercell.R

/** Foreground-service boundary for native Android background microphone sessions. */
class WhisperCellForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val stateLabel: String = intent?.getStringExtra(EXTRA_STATE_LABEL) ?: "WhisperCell session active"
        startForeground(NOTIFICATION_ID, buildNotification(stateLabel))
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "WhisperCell Performance Session",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when WhisperCell is listening, paused, processing, or publishing during a performance."
            setShowBadge(false)
        }
        val notificationManager: NotificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(stateLabel: String): Notification {
        val launchIntent: PendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("WhisperCell session active")
            .setContentText(stateLabel)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(launchIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val EXTRA_STATE_LABEL: String = "extra_state_label"
        private const val CHANNEL_ID: String = "whispercell_performance_session"
        private const val NOTIFICATION_ID: Int = 3907

        fun intent(context: Context, stateLabel: String): Intent = Intent(context, WhisperCellForegroundService::class.java)
            .putExtra(EXTRA_STATE_LABEL, stateLabel)
    }
}
