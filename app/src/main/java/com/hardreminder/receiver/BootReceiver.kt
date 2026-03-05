package com.hardreminder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.hardreminder.alarm.AlarmScheduler
import com.hardreminder.data.AppSettings.startOnBoot
import com.hardreminder.service.ReminderForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in listOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_LOCKED_BOOT_COMPLETED,
                "android.intent.action.QUICKBOOT_POWERON"
            )
        ) {
            Log.d("BootReceiver", "Device booted - rescheduling all alarms")

            // Start foreground service if enabled
            if (context.startOnBoot) {
                val serviceIntent = Intent(context, ReminderForegroundService::class.java)
                context.startForegroundService(serviceIntent)
            }

            // Reschedule all alarms
            CoroutineScope(Dispatchers.IO).launch {
                AlarmScheduler.rescheduleAllAlarms(context)
            }
        }
    }
}
