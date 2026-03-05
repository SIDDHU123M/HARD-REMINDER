package com.hardreminder.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.hardreminder.alarm.AlarmScheduler
import com.hardreminder.data.AppSettings
import com.hardreminder.data.AppSettings.snoozeMinutes
import com.hardreminder.data.ReminderDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SnoozeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra("REMINDER_ID", -1)
        val notificationId = intent.getIntExtra("NOTIFICATION_ID", -1)

        Log.d("SnoozeReceiver", "Snoozing reminder $reminderId")

        if (reminderId == -1L) return

        // Dismiss current notification
        if (notificationId != -1) {
            context.getSystemService(NotificationManager::class.java).cancel(notificationId)
        }

        val snoozeMs = context.snoozeMinutes * 60 * 1000L
        val snoozeTime = System.currentTimeMillis() + snoozeMs

        CoroutineScope(Dispatchers.IO).launch {
            val db = ReminderDatabase.getDatabase(context)
            val reminder = db.reminderDao().getReminderById(reminderId) ?: return@launch

            // Temporarily update trigger time to snooze time
            db.reminderDao().updateTriggerTime(reminderId, snoozeTime)
            AlarmScheduler.scheduleAlarm(context, reminder.copy(triggerTimeMillis = snoozeTime))
        }
    }
}
