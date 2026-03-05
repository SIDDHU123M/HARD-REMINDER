package com.hardreminder

import android.app.Application
import android.content.Intent
import com.hardreminder.alarm.AlarmScheduler
import com.hardreminder.data.AppSettings.showOngoingNotification
import com.hardreminder.data.ReminderDatabase
import com.hardreminder.service.ReminderForegroundService
import com.hardreminder.ui.ThemeHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HardReminderApp : Application() {

    val database: ReminderDatabase by lazy { ReminderDatabase.getDatabase(this) }
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Apply saved theme preference
        ThemeHelper.applyGlobalTheme(this)

        // Start foreground service to keep alive (if enabled)
        if (showOngoingNotification) {
            val serviceIntent = Intent(this, ReminderForegroundService::class.java)
            startForegroundService(serviceIntent)
        }

        // Reschedule all alarms on app start (in case any were missed)
        applicationScope.launch {
            AlarmScheduler.rescheduleAllAlarms(this@HardReminderApp)
        }
    }
}
