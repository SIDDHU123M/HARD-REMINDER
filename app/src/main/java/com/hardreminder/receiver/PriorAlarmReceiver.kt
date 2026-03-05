package com.hardreminder.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hardreminder.R
import com.hardreminder.ui.MainActivity

class PriorAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "hard_reminder_prior"
        const val TAG = "PriorAlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra("REMINDER_ID", -1)
        val title = intent.getStringExtra("REMINDER_TITLE") ?: "Reminder"
        val message = intent.getStringExtra("REMINDER_MESSAGE") ?: ""
        val priorMinutes = intent.getIntExtra("PRIOR_MINUTES", 0)

        Log.d(TAG, "Prior notification for reminder $reminderId ($priorMinutes min before)")

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "HardReminder::PriorWakeLock"
        )
        wakeLock.acquire(10_000)

        try {
            createPriorChannel(context)
            showPriorNotification(context, reminderId, title, message, priorMinutes)
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    private fun createPriorChannel(context: Context) {
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Prior Notifications",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Heads-up notification before reminder fires"
            setSound(sound, audioAttributes)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 200, 100, 200)
        }

        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun showPriorNotification(
        context: Context,
        reminderId: Long,
        title: String,
        message: String,
        priorMinutes: Int
    ) {
        val notifId = reminderId.toInt() + 40000

        // Cancel upcoming reminder action
        val cancelIntent = Intent(context, CancelReminderReceiver::class.java).apply {
            putExtra("REMINDER_ID", reminderId)
            putExtra("NOTIFICATION_ID", notifId)
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context, reminderId.toInt() + 50000, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Dismiss this notification (keep reminder active)
        val dismissIntent = Intent(context, DismissReceiver::class.java).apply {
            putExtra("NOTIFICATION_ID", notifId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context, reminderId.toInt() + 60000, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Open app
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context, reminderId.toInt() + 70000, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeLabel = if (priorMinutes >= 60) {
            val h = priorMinutes / 60
            val m = priorMinutes % 60
            if (m > 0) "${h}h ${m}m" else "${h} hour${if (h > 1) "s" else ""}"
        } else "$priorMinutes min"

        val notifText = if (message.isNotEmpty()) {
            "$message\n\nFiring in $timeLabel. Cancel if not needed."
        } else {
            "Firing in $timeLabel. Cancel if you're busy."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Upcoming: $title")
            .setContentText("Fires in $timeLabel")
            .setStyle(NotificationCompat.BigTextStyle().bigText(notifText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .addAction(R.drawable.ic_dismiss, "Cancel Reminder", cancelPendingIntent)
            .addAction(R.drawable.ic_notification, "OK, Keep It", dismissPendingIntent)

        context.getSystemService(NotificationManager::class.java)
            .notify(notifId, builder.build())
    }
}
