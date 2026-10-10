package com.benjaminbini.wallswap

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder

/**
 * Android stops delivering sensor events to apps in the background. This service keeps WallSwap
 * in the foreground, so a background change can read the hinge angle to find the screen in use.
 */
class HingeAccessService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, "Fold detection", NotificationManager.IMPORTANCE_MIN),
        )
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle("Watching the fold state")
            .setContentText("Lets WallSwap put each wallpaper on the right screen")
            .setContentIntent(openApp)
            .build()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    companion object {
        private const val CHANNEL = "fold-detection"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, HingeAccessService::class.java))
        }
    }
}

/** Restarts [HingeAccessService] after a reboot or an app update. */
class StartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        HingeAccessService.start(context)
    }
}
