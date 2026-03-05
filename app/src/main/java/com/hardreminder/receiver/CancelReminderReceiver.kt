package com.hardreminder.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.hardreminder.alarm.AlarmScheduler
import com.hardreminder.data.ReminderDatabase
import com.hardreminder.data.RepeatType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CancelReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra("REMINDER_ID", -1)
        val notificationId = intent.getIntExtra("NOTIFICATION_ID", -1)
        val cancelFuture = intent.getBooleanExtra("CANCEL_FUTURE", false)

        Log.d("CancelReminderReceiver", "Cancelling reminder $reminderId, future=$cancelFuture")

        if (reminderId == -1L) return

        // Dismiss the prior notification
        if (notificationId != -1) {
            context.getSystemService(NotificationManager::class.java).cancel(notificationId)
        }

        // Cancel the upcoming alarm
        AlarmScheduler.cancelAlarm(context, reminderId)
        AlarmScheduler.cancelPriorAlarm(context, reminderId)

        CoroutineScope(Dispatchers.IO).launch {
            val db = ReminderDatabase.getDatabase(context)
            val reminder = db.reminderDao().getReminderById(reminderId) ?: return@launch

            if (cancelFuture || reminder.repeatType == RepeatType.NONE) {
                // Disable completely
                db.reminderDao().setEnabled(reminderId, false)
            } else {
                // Just skip this occurrence - reschedule next one
                val nextTime = AlarmScheduler.calculateNextTriggerTime(reminder)
                if (nextTime != null) {
                    db.reminderDao().updateTriggerTime(reminderId, nextTime)
                    val updated = reminder.copy(triggerTimeMillis = nextTime)
                    AlarmScheduler.scheduleAlarm(context, updated)
                    AlarmScheduler.schedulePriorAlarm(context, updated)
                } else {
                    db.reminderDao().setEnabled(reminderId, false)
                }
            }
        }
    }
}
