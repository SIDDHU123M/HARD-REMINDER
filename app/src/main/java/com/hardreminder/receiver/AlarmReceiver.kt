package com.hardreminder.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hardreminder.R
import com.hardreminder.alarm.AlarmScheduler
import com.hardreminder.data.AppSettings
import com.hardreminder.data.AppSettings.autoDeleteFired
import com.hardreminder.data.ReminderDatabase
import com.hardreminder.data.RepeatType
import com.hardreminder.ui.AlarmRingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "hard_reminder_alarm"
        const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra("REMINDER_ID", -1)
        val title = intent.getStringExtra("REMINDER_TITLE") ?: "Reminder"
        val message = intent.getStringExtra("REMINDER_MESSAGE") ?: ""
        val soundEnabled = intent.getBooleanExtra("REMINDER_SOUND", true)
        val vibrateEnabled = intent.getBooleanExtra("REMINDER_VIBRATE", true)

        Log.d(TAG, "Alarm received for reminder $reminderId: $title")

        // Acquire wake lock to ensure processing completes
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "HardReminder::AlarmWakeLock"
        )
        wakeLock.acquire(30_000) // 30 seconds max

        try {
            createNotificationChannel(context)
            showNotification(context, reminderId.toInt(), title, message, soundEnabled)

            if (vibrateEnabled) {
                triggerVibration(context)
            }

            // Launch full-screen alarm activity
            launchAlarmScreen(context, reminderId, title, message, soundEnabled)

            // Handle repeat scheduling
            if (reminderId != -1L) {
                handleRepeat(context, reminderId)
            }
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }

    private fun createNotificationChannel(context: Context) {
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Reminder Alarms",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alarm notifications for reminders"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
            setSound(alarmSound, audioAttributes)
            setBypassDnd(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun showNotification(
        context: Context,
        notifId: Int,
        title: String,
        message: String,
        soundEnabled: Boolean
    ) {
        // Dismiss action
        val dismissIntent = Intent(context, DismissReceiver::class.java).apply {
            putExtra("NOTIFICATION_ID", notifId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            notifId + 20000,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze action
        val snoozeIntent = Intent(context, SnoozeReceiver::class.java).apply {
            putExtra("REMINDER_ID", notifId.toLong())
            putExtra("NOTIFICATION_ID", notifId)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            notifId + 90000,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Full screen intent to show alarm screen
        val fullScreenIntent = Intent(context, AlarmRingActivity::class.java).apply {
            putExtra("REMINDER_ID", notifId.toLong())
            putExtra("REMINDER_TITLE", title)
            putExtra("REMINDER_MESSAGE", message)
            putExtra("REMINDER_SOUND", soundEnabled)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            notifId + 30000,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message.ifEmpty { "Reminder!" })
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(R.drawable.ic_dismiss, "Dismiss", dismissPendingIntent)
            .addAction(R.drawable.ic_notification, "Snooze", snoozePendingIntent)
            .setOngoing(true)

        if (soundEnabled) {
            builder.setSound(alarmSound)
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(notifId, builder.build())
    }

    private fun triggerVibration(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = longArrayOf(0, 500, 200, 500, 200, 500, 200, 500)
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    private fun launchAlarmScreen(
        context: Context,
        reminderId: Long,
        title: String,
        message: String,
        soundEnabled: Boolean
    ) {
        val alarmIntent = Intent(context, AlarmRingActivity::class.java).apply {
            putExtra("REMINDER_ID", reminderId)
            putExtra("REMINDER_TITLE", title)
            putExtra("REMINDER_MESSAGE", message)
            putExtra("REMINDER_SOUND", soundEnabled)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
        }
        context.startActivity(alarmIntent)
    }

    private fun handleRepeat(context: Context, reminderId: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = ReminderDatabase.getDatabase(context)
            val reminder = db.reminderDao().getReminderById(reminderId) ?: return@launch

            if (reminder.repeatType == RepeatType.NONE) {
                // One-time reminder
                if (context.autoDeleteFired) {
                    db.reminderDao().deleteById(reminderId)
                } else {
                    db.reminderDao().setEnabled(reminderId, false)
                }
            } else {
                // Calculate next trigger time and reschedule
                val nextTime = AlarmScheduler.calculateNextTriggerTime(reminder)
                if (nextTime != null) {
                    db.reminderDao().updateTriggerTime(reminderId, nextTime)
                    val updated = reminder.copy(triggerTimeMillis = nextTime)
                    AlarmScheduler.scheduleAlarm(context, updated)
                    AlarmScheduler.schedulePriorAlarm(context, updated)
                    Log.d(TAG, "Next alarm for $reminderId scheduled at $nextTime")
                } else {
                    db.reminderDao().setEnabled(reminderId, false)
                }
            }
        }
    }
}
