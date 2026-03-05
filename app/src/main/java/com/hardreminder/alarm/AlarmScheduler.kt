package com.hardreminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.hardreminder.data.Reminder
import com.hardreminder.data.ReminderDatabase
import com.hardreminder.data.RepeatType
import com.hardreminder.receiver.AlarmReceiver
import com.hardreminder.receiver.PriorAlarmReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

object AlarmScheduler {

    private const val TAG = "AlarmScheduler"

    fun scheduleAlarm(context: Context, reminder: Reminder) {
        if (!reminder.isEnabled) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("REMINDER_ID", reminder.id)
            putExtra("REMINDER_TITLE", reminder.title)
            putExtra("REMINDER_MESSAGE", reminder.message)
            putExtra("REMINDER_SOUND", reminder.soundEnabled)
            putExtra("REMINDER_VIBRATE", reminder.vibrationEnabled)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use setAlarmClock for maximum reliability - this is treated as high priority
        // and will fire even in Doze mode. Shows an alarm icon in status bar too.
        val alarmClockInfo = AlarmManager.AlarmClockInfo(
            reminder.triggerTimeMillis,
            getShowIntent(context, reminder.id)
        )

        try {
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            Log.d(TAG, "Alarm scheduled for reminder ${reminder.id} at ${reminder.triggerTimeMillis}")
        } catch (e: SecurityException) {
            // Fallback to exact alarm if setAlarmClock fails
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.triggerTimeMillis,
                    pendingIntent
                )
                Log.d(TAG, "Fallback exact alarm scheduled for reminder ${reminder.id}")
            } catch (e2: SecurityException) {
                Log.e(TAG, "Cannot schedule exact alarm for reminder ${reminder.id}", e2)
            }
        }
    }

    fun cancelAlarm(context: Context, reminderId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        Log.d(TAG, "Alarm cancelled for reminder $reminderId")
    }

    fun schedulePriorAlarm(context: Context, reminder: Reminder) {
        if (!reminder.isEnabled || reminder.priorNotifyMinutes <= 0) return

        val priorTimeMillis = reminder.triggerTimeMillis - (reminder.priorNotifyMinutes * 60 * 1000L)
        if (priorTimeMillis <= System.currentTimeMillis()) return // Too late for prior notif

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, PriorAlarmReceiver::class.java).apply {
            putExtra("REMINDER_ID", reminder.id)
            putExtra("REMINDER_TITLE", reminder.title)
            putExtra("REMINDER_MESSAGE", reminder.message)
            putExtra("PRIOR_MINUTES", reminder.priorNotifyMinutes)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.toInt() + 80000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                priorTimeMillis,
                pendingIntent
            )
            Log.d(TAG, "Prior alarm scheduled for reminder ${reminder.id} at $priorTimeMillis")
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot schedule prior alarm for reminder ${reminder.id}", e)
        }
    }

    fun cancelPriorAlarm(context: Context, reminderId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, PriorAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt() + 80000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    /**
     * Calculate the next trigger time for a repeating reminder.
     * Returns null if the reminder should not repeat.
     */
    fun calculateNextTriggerTime(reminder: Reminder): Long? {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            timeInMillis = reminder.triggerTimeMillis
        }

        return when (reminder.repeatType) {
            RepeatType.NONE -> null

            RepeatType.DAILY -> {
                // Add 1 day, if still in past keep adding
                do {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                } while (calendar.timeInMillis <= now)
                calendar.timeInMillis
            }

            RepeatType.WEEKDAYS -> {
                do {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                } while (calendar.timeInMillis <= now ||
                    calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                    calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
                calendar.timeInMillis
            }

            RepeatType.WEEKENDS -> {
                do {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                } while (calendar.timeInMillis <= now ||
                    (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY &&
                            calendar.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY))
                calendar.timeInMillis
            }

            RepeatType.WEEKLY -> {
                // repeatData = comma-separated day numbers (1=Monday..7=Sunday)
                val days = reminder.repeatData.split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                    .map { isoToCalendarDay(it) }
                    .toSet()

                if (days.isEmpty()) return null

                do {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                } while (calendar.timeInMillis <= now ||
                    calendar.get(Calendar.DAY_OF_WEEK) !in days)
                calendar.timeInMillis
            }

            RepeatType.CUSTOM -> {
                val intervalDays = reminder.repeatData.toIntOrNull() ?: return null
                if (intervalDays <= 0) return null
                do {
                    calendar.add(Calendar.DAY_OF_YEAR, intervalDays)
                } while (calendar.timeInMillis <= now)
                calendar.timeInMillis
            }
        }
    }

    /**
     * Reschedule all enabled alarms. Called after boot, app update, etc.
     */
    suspend fun rescheduleAllAlarms(context: Context) {
        withContext(Dispatchers.IO) {
            val db = ReminderDatabase.getDatabase(context)
            val reminders = db.reminderDao().getEnabledReminders()
            val now = System.currentTimeMillis()

            for (reminder in reminders) {
                if (reminder.triggerTimeMillis > now) {
                    // Future alarm - schedule it
                    scheduleAlarm(context, reminder)
                    schedulePriorAlarm(context, reminder)
                } else if (reminder.repeatType != RepeatType.NONE) {
                    // Past alarm with repeat - calculate next and reschedule
                    val nextTime = calculateNextTriggerTime(reminder)
                    if (nextTime != null) {
                        db.reminderDao().updateTriggerTime(reminder.id, nextTime)
                        scheduleAlarm(context, reminder.copy(triggerTimeMillis = nextTime))
                        schedulePriorAlarm(context, reminder.copy(triggerTimeMillis = nextTime))
                    } else {
                        db.reminderDao().setEnabled(reminder.id, false)
                    }
                } else {
                    // Past one-time alarm - disable it
                    db.reminderDao().setEnabled(reminder.id, false)
                }
            }
            Log.d(TAG, "Rescheduled ${reminders.size} alarms")
        }
    }

    private fun getShowIntent(context: Context, reminderId: Long): PendingIntent {
        val intent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply { putExtra("REMINDER_ID", reminderId) }
            ?: Intent()

        return PendingIntent.getActivity(
            context,
            reminderId.toInt() + 10000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Convert ISO day (1=Monday..7=Sunday) to Calendar day constant
     */
    private fun isoToCalendarDay(isoDay: Int): Int {
        return when (isoDay) {
            1 -> Calendar.MONDAY
            2 -> Calendar.TUESDAY
            3 -> Calendar.WEDNESDAY
            4 -> Calendar.THURSDAY
            5 -> Calendar.FRIDAY
            6 -> Calendar.SATURDAY
            7 -> Calendar.SUNDAY
            else -> Calendar.MONDAY
        }
    }
}
