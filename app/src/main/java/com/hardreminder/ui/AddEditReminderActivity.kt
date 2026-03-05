package com.hardreminder.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hardreminder.HardReminderApp
import com.hardreminder.alarm.AlarmScheduler
import com.hardreminder.data.AppSettings
import com.hardreminder.data.AppSettings.defaultPriorMinutes
import com.hardreminder.data.AppSettings.defaultSound
import com.hardreminder.data.AppSettings.defaultVibration
import com.hardreminder.data.AppSettings.use24HourFormat
import com.hardreminder.data.Reminder
import com.hardreminder.data.RepeatType
import com.hardreminder.databinding.ActivityAddEditReminderBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddEditReminderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddEditReminderBinding
    private val calendar = Calendar.getInstance()
    private var editingReminderId: Long = -1
    private var existingReminder: Reminder? = null

    private val dateFormat = SimpleDateFormat("EEE, MMM dd, yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    private val repeatOptions = arrayOf("No Repeat", "Daily", "Weekdays (Mon-Fri)", "Weekends (Sat-Sun)", "Specific Days", "Custom Interval")
    private var selectedRepeatIndex = 0
    private var selectedPriorIndex = 0

    private val dayNames = arrayOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    private val selectedDays = BooleanArray(7) // Mon=0, Tue=1, ..., Sun=6

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeHelper.applyActivityTheme(this)
        binding = ActivityAddEditReminderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        editingReminderId = intent.getLongExtra("REMINDER_ID", -1)

        setupRepeatSpinner()
        setupPriorSpinner()
        setupDateTimePickers()
        setupDayChips()
        setupSaveButton()

        if (editingReminderId != -1L) {
            supportActionBar?.title = "Edit Reminder"
            loadExistingReminder()
        } else {
            supportActionBar?.title = "New Reminder"
            // Default to 1 minute from now
            calendar.add(Calendar.MINUTE, 1)
            updateDateTimeDisplay()
            // Apply defaults from settings
            binding.switchSound.isChecked = defaultSound
            binding.switchVibration.isChecked = defaultVibration
            // Set default prior notification from settings
            val defaultPrior = defaultPriorMinutes
            val priorIndex = AppSettings.PRIOR_OPTIONS.indexOfFirst { it.first == defaultPrior }.coerceAtLeast(0)
            selectedPriorIndex = priorIndex
            binding.spinnerPrior.setText(AppSettings.PRIOR_OPTIONS[priorIndex].second, false)
        }
    }

    private fun setupRepeatSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, repeatOptions)
        binding.spinnerRepeat.setAdapter(adapter)
        binding.spinnerRepeat.setText(repeatOptions[0], false)

        binding.spinnerRepeat.setOnItemClickListener { _, _, position, _ ->
            selectedRepeatIndex = position
            updateRepeatOptionsVisibility(position)
        }
    }

    private fun updateRepeatOptionsVisibility(position: Int) {
        binding.layoutDayChips.visibility = if (position == 4) View.VISIBLE else View.GONE
        binding.layoutCustomInterval.visibility = if (position == 5) View.VISIBLE else View.GONE
    }

    private fun setupPriorSpinner() {
        val priorLabels = AppSettings.PRIOR_OPTIONS.map { it.second }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, priorLabels)
        binding.spinnerPrior.setAdapter(adapter)
        binding.spinnerPrior.setText(priorLabels[0], false)

        binding.spinnerPrior.setOnItemClickListener { _, _, position, _ ->
            selectedPriorIndex = position
        }
    }

    private fun setupDateTimePickers() {
        binding.btnDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    calendar.set(Calendar.YEAR, year)
                    calendar.set(Calendar.MONTH, month)
                    calendar.set(Calendar.DAY_OF_MONTH, day)
                    updateDateTimeDisplay()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).apply {
                datePicker.minDate = System.currentTimeMillis() - 1000
            }.show()
        }

        binding.btnTime.setOnClickListener {
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    calendar.set(Calendar.HOUR_OF_DAY, hour)
                    calendar.set(Calendar.MINUTE, minute)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    updateDateTimeDisplay()
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                use24HourFormat
            ).show()
        }
    }

    private fun setupDayChips() {
        val chips = listOf(
            binding.chipMon, binding.chipTue, binding.chipWed,
            binding.chipThu, binding.chipFri, binding.chipSat, binding.chipSun
        )

        chips.forEachIndexed { index, chip ->
            chip.setOnCheckedChangeListener { _, isChecked ->
                selectedDays[index] = isChecked
            }
        }
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            saveReminder()
        }
    }

    private fun updateDateTimeDisplay() {
        binding.btnDate.text = dateFormat.format(calendar.time)
        binding.btnTime.text = timeFormat.format(calendar.time)
    }

    private fun loadExistingReminder() {
        val dao = (application as HardReminderApp).database.reminderDao()
        lifecycleScope.launch {
            val reminder = dao.getReminderById(editingReminderId) ?: run {
                Toast.makeText(this@AddEditReminderActivity, "Reminder not found", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            existingReminder = reminder

            binding.editTitle.setText(reminder.title)
            binding.editMessage.setText(reminder.message)
            binding.switchSound.isChecked = reminder.soundEnabled
            binding.switchVibration.isChecked = reminder.vibrationEnabled

            calendar.timeInMillis = reminder.triggerTimeMillis
            updateDateTimeDisplay()

            // Set repeat type
            val repeatIndex = when (reminder.repeatType) {
                RepeatType.NONE -> 0
                RepeatType.DAILY -> 1
                RepeatType.WEEKDAYS -> 2
                RepeatType.WEEKENDS -> 3
                RepeatType.WEEKLY -> 4
                RepeatType.CUSTOM -> 5
            }
            binding.spinnerRepeat.setText(repeatOptions[repeatIndex], false)
            selectedRepeatIndex = repeatIndex

            // Load weekly days
            if (reminder.repeatType == RepeatType.WEEKLY) {
                val days = reminder.repeatData.split(",").mapNotNull { it.trim().toIntOrNull() }
                val chips = listOf(
                    binding.chipMon, binding.chipTue, binding.chipWed,
                    binding.chipThu, binding.chipFri, binding.chipSat, binding.chipSun
                )
                days.forEach { day ->
                    if (day in 1..7) {
                        chips[day - 1].isChecked = true
                        selectedDays[day - 1] = true
                    }
                }
            }

            // Load custom interval
            if (reminder.repeatType == RepeatType.CUSTOM) {
                binding.editCustomDays.setText(reminder.repeatData)
            }

            // Load prior notification setting
            val priorIndex = AppSettings.PRIOR_OPTIONS.indexOfFirst { it.first == reminder.priorNotifyMinutes }.coerceAtLeast(0)
            selectedPriorIndex = priorIndex
            binding.spinnerPrior.setText(AppSettings.PRIOR_OPTIONS[priorIndex].second, false)
        }
    }

    private fun saveReminder() {
        val title = binding.editTitle.text.toString().trim()
        if (title.isEmpty()) {
            binding.editTitle.error = "Title required"
            return
        }

        val message = binding.editMessage.text.toString().trim()
        val triggerTime = calendar.timeInMillis
        val soundEnabled = binding.switchSound.isChecked
        val vibrationEnabled = binding.switchVibration.isChecked

        val repeatType = when (selectedRepeatIndex) {
            0 -> RepeatType.NONE
            1 -> RepeatType.DAILY
            2 -> RepeatType.WEEKDAYS
            3 -> RepeatType.WEEKENDS
            4 -> RepeatType.WEEKLY
            5 -> RepeatType.CUSTOM
            else -> RepeatType.NONE
        }

        // Validate
        if (repeatType == RepeatType.NONE && triggerTime <= System.currentTimeMillis()) {
            Toast.makeText(this, "Please select a future time", Toast.LENGTH_SHORT).show()
            return
        }

        val repeatData = when (repeatType) {
            RepeatType.WEEKLY -> {
                val days = selectedDays.toList().mapIndexedNotNull { index, selected ->
                    if (selected) (index + 1).toString() else null
                }
                if (days.isEmpty()) {
                    Toast.makeText(this, "Select at least one day", Toast.LENGTH_SHORT).show()
                    return
                }
                days.joinToString(",")
            }
            RepeatType.CUSTOM -> {
                val interval = binding.editCustomDays.text.toString().trim()
                val intervalDays = interval.toIntOrNull()
                if (intervalDays == null || intervalDays <= 0) {
                    binding.editCustomDays.error = "Enter a valid number of days"
                    return
                }
                interval
            }
            else -> ""
        }

        val priorMinutes = AppSettings.PRIOR_OPTIONS
            .getOrNull(selectedPriorIndex)
            ?.first ?: 0

        val reminder = Reminder(
            id = if (editingReminderId != -1L) editingReminderId else 0,
            title = title,
            message = message,
            triggerTimeMillis = triggerTime,
            isEnabled = true,
            repeatType = repeatType,
            repeatData = repeatData,
            soundEnabled = soundEnabled,
            vibrationEnabled = vibrationEnabled,
            priorNotifyMinutes = priorMinutes,
            createdAt = existingReminder?.createdAt ?: System.currentTimeMillis()
        )

        val dao = (application as HardReminderApp).database.reminderDao()
        lifecycleScope.launch {
            if (editingReminderId != -1L) {
                // Cancel old alarm first
                AlarmScheduler.cancelAlarm(this@AddEditReminderActivity, editingReminderId)
                AlarmScheduler.cancelPriorAlarm(this@AddEditReminderActivity, editingReminderId)
                dao.updateReminder(reminder)
            } else {
                val newId = dao.insertReminder(reminder)
                val saved = reminder.copy(id = newId)
                AlarmScheduler.scheduleAlarm(this@AddEditReminderActivity, saved)
                AlarmScheduler.schedulePriorAlarm(this@AddEditReminderActivity, saved)
                finish()
                return@launch
            }

            AlarmScheduler.scheduleAlarm(this@AddEditReminderActivity, reminder)
            AlarmScheduler.schedulePriorAlarm(this@AddEditReminderActivity, reminder)
            finish()
        }
    }
}
