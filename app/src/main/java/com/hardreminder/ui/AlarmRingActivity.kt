package com.hardreminder.ui

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.hardreminder.data.AppSettings.snoozeMinutes
import com.hardreminder.alarm.AlarmScheduler
import com.hardreminder.data.ReminderDatabase
import com.hardreminder.databinding.ActivityAlarmRingBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmRingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmRingBinding
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(KeyguardManager::class.java)
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        binding = ActivityAlarmRingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val reminderId = intent.getLongExtra("REMINDER_ID", -1)
        val title = intent.getStringExtra("REMINDER_TITLE") ?: "Reminder"
        val message = intent.getStringExtra("REMINDER_MESSAGE") ?: ""
        val soundEnabled = intent.getBooleanExtra("REMINDER_SOUND", true)

        binding.textAlarmTitle.text = title
        binding.textAlarmMessage.text = message.ifEmpty { "Time's up!" }

        if (soundEnabled) {
            startAlarmSound()
        }
        startVibration()

        binding.btnDismiss.setOnClickListener {
            dismiss(reminderId)
        }

        binding.btnSnooze.setOnClickListener {
            snooze(reminderId)
        }
    }

    private fun snooze(reminderId: Long) {
        stopAlarm()
        if (reminderId != -1L) {
            getSystemService(NotificationManager::class.java).cancel(reminderId.toInt())
            val snoozeMs = snoozeMinutes * 60 * 1000L
            val snoozeTime = System.currentTimeMillis() + snoozeMs
            CoroutineScope(Dispatchers.IO).launch {
                val db = ReminderDatabase.getDatabase(this@AlarmRingActivity)
                val reminder = db.reminderDao().getReminderById(reminderId) ?: return@launch
                db.reminderDao().updateTriggerTime(reminderId, snoozeTime)
                AlarmScheduler.scheduleAlarm(this@AlarmRingActivity, reminder.copy(triggerTimeMillis = snoozeTime))
            }
        }
        finishAndRemoveTask()
    }

    private fun startAlarmSound() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmRingActivity, alarmUri)
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) {
            // Fallback - try default notification sound
            try {
                val notifUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .build()
                    )
                    setDataSource(this@AlarmRingActivity, notifUri)
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (_: Exception) { }
        }
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(VibratorManager::class.java)
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = longArrayOf(0, 800, 400, 800, 400, 800, 400)
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0)) // 0 = repeat from index 0
    }

    private fun dismiss(reminderId: Long) {
        stopAlarm()
        // Cancel the notification too
        if (reminderId != -1L) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.cancel(reminderId.toInt())
        }
        finishAndRemoveTask()
    }

    private fun stopAlarm() {
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null

        vibrator?.cancel()
        vibrator = null
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }

    @Deprecated("Use onBackPressedDispatcher")
    override fun onBackPressed() {
        // Prevent dismissing with back button - must press Dismiss
        // Do nothing
    }
}
