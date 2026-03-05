package com.hardreminder.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hardreminder.R
import com.hardreminder.BuildConfig
import com.hardreminder.data.AppSettings
import com.hardreminder.data.AppSettings.autoDeleteFired
import com.hardreminder.data.AppSettings.defaultPriorMinutes
import com.hardreminder.data.AppSettings.defaultSound
import com.hardreminder.data.AppSettings.defaultVibration
import com.hardreminder.data.AppSettings.flashScreenOnAlarm
import com.hardreminder.data.AppSettings.showOngoingNotification
import com.hardreminder.data.AppSettings.snoozeMinutes
import com.hardreminder.data.AppSettings.startOnBoot
import com.hardreminder.data.AppSettings.colorPalette
import com.hardreminder.data.AppSettings.themeMode
import com.hardreminder.data.AppSettings.use24HourFormat
import com.hardreminder.data.AppSettings.useAmoledMode
import com.hardreminder.databinding.ActivitySettingsBinding
import com.hardreminder.service.ReminderForegroundService

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeHelper.applyActivityTheme(this)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Settings"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        loadCurrentSettings()
        setupListeners()
    }

    private fun loadCurrentSettings() {
        binding.switchDefaultSound.isChecked = defaultSound
        binding.switchDefaultVibration.isChecked = defaultVibration
        binding.switch24Hour.isChecked = use24HourFormat
        binding.switchAutoDelete.isChecked = autoDeleteFired
        binding.switchStartOnBoot.isChecked = startOnBoot
        binding.switchOngoingNotif.isChecked = showOngoingNotification
        binding.switchFlashScreen.isChecked = flashScreenOnAlarm

        binding.textAboutVersion.text = "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"

        updateThemeToggleGroup()
        binding.switchAmoled.isChecked = useAmoledMode

        updatePaletteLabel()
        updateSnoozeLabel()
        updatePriorLabel()
        updateBatteryStatus()
    }

    private fun setupListeners() {
        binding.switchDefaultSound.setOnCheckedChangeListener { _, checked ->
            defaultSound = checked
        }

        binding.switchDefaultVibration.setOnCheckedChangeListener { _, checked ->
            defaultVibration = checked
        }

        binding.switch24Hour.setOnCheckedChangeListener { _, checked ->
            use24HourFormat = checked
        }

        binding.switchAutoDelete.setOnCheckedChangeListener { _, checked ->
            autoDeleteFired = checked
        }

        binding.switchStartOnBoot.setOnCheckedChangeListener { _, checked ->
            startOnBoot = checked
        }

        binding.switchOngoingNotif.setOnCheckedChangeListener { _, checked ->
            showOngoingNotification = checked
            if (checked) {
                startForegroundService(Intent(this, ReminderForegroundService::class.java))
            } else {
                stopService(Intent(this, ReminderForegroundService::class.java))
            }
        }

        binding.switchFlashScreen.setOnCheckedChangeListener { _, checked ->
            flashScreenOnAlarm = checked
        }

        // Palette picker
        binding.layoutPalette.setOnClickListener {
            val options = AppSettings.PALETTE_OPTIONS
            val labels = options.map { it.second }.toTypedArray()
            val currentIndex = options.indexOfFirst { it.first == colorPalette }.coerceAtLeast(0)

            MaterialAlertDialogBuilder(this)
                .setTitle("Color Palette")
                .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                    colorPalette = options[which].first
                    updatePaletteLabel()
                    dialog.dismiss()
                    recreate()
                }
                .show()
        }

        // Theme picker via ToggleGroup
        binding.toggleThemeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val newMode = when (checkedId) {
                    R.id.btnThemeSystem -> AppSettings.THEME_SYSTEM
                    R.id.btnThemeDark -> AppSettings.THEME_DARK
                    R.id.btnThemeLight -> AppSettings.THEME_LIGHT
                    else -> AppSettings.THEME_SYSTEM
                }
                if (themeMode != newMode) {
                    themeMode = newMode
                    ThemeHelper.applyGlobalTheme(this)
                    recreate()
                }
            }
        }

        // AMOLED toggle
        binding.switchAmoled.setOnCheckedChangeListener { _, checked ->
            if (useAmoledMode != checked) {
                useAmoledMode = checked
                recreate()
            }
        }
        binding.layoutAmoled.setOnClickListener {
            binding.switchAmoled.toggle()
        }

        // Snooze duration picker
        binding.layoutSnooze.setOnClickListener {
            val options = AppSettings.SNOOZE_OPTIONS
            val labels = options.map { it.second }.toTypedArray()
            val currentIndex = options.indexOfFirst { it.first == snoozeMinutes }.coerceAtLeast(0)

            MaterialAlertDialogBuilder(this)
                .setTitle("Snooze Duration")
                .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                    snoozeMinutes = options[which].first
                    updateSnoozeLabel()
                    dialog.dismiss()
                }
                .show()
        }

        // Default prior notification picker
        binding.layoutDefaultPrior.setOnClickListener {
            val options = AppSettings.PRIOR_OPTIONS
            val labels = options.map { it.second }.toTypedArray()
            val currentIndex = options.indexOfFirst { it.first == defaultPriorMinutes }.coerceAtLeast(0)

            MaterialAlertDialogBuilder(this)
                .setTitle("Default Prior Notification")
                .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                    defaultPriorMinutes = options[which].first
                    updatePriorLabel()
                    dialog.dismiss()
                }
                .show()
        }

        // Battery optimization
        binding.layoutBattery.setOnClickListener {
            val pm = getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } else {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Battery Optimization")
                    .setMessage("Already disabled. Reminders will fire reliably.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }

        // Notification settings
        binding.layoutNotifSettings.setOnClickListener {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
            startActivity(intent)
        }

        // About
        binding.layoutAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        updateBatteryStatus()
    }

    private fun updateThemeToggleGroup() {
        val buttonId = when (themeMode) {
            AppSettings.THEME_SYSTEM -> R.id.btnThemeSystem
            AppSettings.THEME_DARK -> R.id.btnThemeDark
            AppSettings.THEME_LIGHT -> R.id.btnThemeLight
            else -> R.id.btnThemeSystem
        }
        binding.toggleThemeGroup.check(buttonId)
    }

    private fun updatePaletteLabel() {
        val label = AppSettings.PALETTE_OPTIONS.firstOrNull { it.first == colorPalette }?.second ?: "Default"
        binding.textPaletteValue.text = label
    }

    private fun updateSnoozeLabel() {
        val label = AppSettings.SNOOZE_OPTIONS.firstOrNull { it.first == snoozeMinutes }?.second ?: "$snoozeMinutes min"
        binding.textSnoozeValue.text = label
    }

    private fun updatePriorLabel() {
        binding.textPriorValue.text = AppSettings.getPriorLabel(defaultPriorMinutes)
    }

    private fun updateBatteryStatus() {
        val pm = getSystemService(PowerManager::class.java)
        val exempt = pm.isIgnoringBatteryOptimizations(packageName)
        binding.textBatteryStatus.text = if (exempt) "Unrestricted" else "Restricted (tap to fix)"
        binding.textBatteryStatus.setTextColor(
            if (exempt) getColor(R.color.status_ok)
            else getColor(R.color.status_error)
        )
    }
}
