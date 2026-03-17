package com.hardreminder.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.hardreminder.HardReminderApp
import com.hardreminder.alarm.AlarmScheduler
import com.hardreminder.data.AppSettings
import com.hardreminder.data.AppSettings.defaultPriorMinutes
import com.hardreminder.data.AppSettings.defaultSound
import com.hardreminder.data.AppSettings.defaultVibration
import com.hardreminder.data.AppSettings.isDarkAppTheme
import com.hardreminder.data.AppSettings.use24HourFormat
import com.hardreminder.data.AppSettings.useAmoledMode
import com.hardreminder.data.AppSettings.useMaterialYou
import com.hardreminder.data.Reminder
import com.hardreminder.data.RepeatType
import com.hardreminder.ui.theme.HardReminderTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import com.hardreminder.R
import kotlinx.coroutines.CancellationException

class AddEditReminderActivity : ComponentActivity() {

    private val calendar = Calendar.getInstance()
    private var editingReminderId: Long = -1
    private val themeRefreshTrigger = mutableIntStateOf(0)

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    override fun onResume() {
        super.onResume()
        themeRefreshTrigger.intValue++
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editingReminderId = intent.getLongExtra("REMINDER_ID", -1)
        val dao = (application as HardReminderApp).database.reminderDao()
        val isEditing = editingReminderId != -1L

        setContent {
            var backProgress by remember { mutableFloatStateOf(0f) }

            PredictiveBackHandler { progress ->
                try {
                    progress.collect { backEvent ->
                        backProgress = backEvent.progress
                    }
                    finish()
                } catch (e: CancellationException) {
                    backProgress = 0f
                }
            }

            val refreshKey = themeRefreshTrigger.intValue
            val context = LocalContext.current
            var title by remember { mutableStateOf("") }
            var message by remember { mutableStateOf("") }
            var triggerTime by remember { mutableStateOf(calendar.timeInMillis) }
            var soundEnabled by remember { mutableStateOf(context.defaultSound) }
            var vibrationEnabled by remember { mutableStateOf(context.defaultVibration) }

            var priorIndex by remember { mutableStateOf(
                AppSettings.PRIOR_OPTIONS.indexOfFirst { it.first == context.defaultPriorMinutes }.coerceAtLeast(0)
            ) }

            var repeatType by remember { mutableStateOf(RepeatType.NONE) }
            var selectedDays by remember { mutableStateOf(BooleanArray(7)) }
            var customInterval by remember { mutableStateOf("") }

            var isLoaded by remember { mutableStateOf(false) }
            var existingReminder by remember { mutableStateOf<Reminder?>(null) }

            // M3 Date/Time picker state
            var showDatePicker by remember { mutableStateOf(false) }
            var showTimePicker by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                if (isEditing) {
                    val reminder = dao.getReminderById(editingReminderId)
                    if (reminder == null) {
                        Toast.makeText(context, "Reminder not found", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        existingReminder = reminder
                        title = reminder.title
                        message = reminder.message
                        soundEnabled = reminder.soundEnabled
                        vibrationEnabled = reminder.vibrationEnabled
                        triggerTime = reminder.triggerTimeMillis
                        calendar.timeInMillis = triggerTime

                        repeatType = reminder.repeatType
                        if (repeatType == RepeatType.WEEKLY) {
                            val days = reminder.repeatData.split(",").mapNotNull { it.trim().toIntOrNull() }
                            val newSelectedDays = BooleanArray(7)
                            days.forEach { day ->
                                if (day in 1..7) newSelectedDays[day - 1] = true
                            }
                            selectedDays = newSelectedDays
                        } else if (repeatType == RepeatType.CUSTOM) {
                            customInterval = reminder.repeatData
                        }

                        priorIndex = AppSettings.PRIOR_OPTIONS.indexOfFirst { it.first == reminder.priorNotifyMinutes }.coerceAtLeast(0)
                        isLoaded = true
                    }
                } else {
                    calendar.add(Calendar.MINUTE, 1)
                    triggerTime = calendar.timeInMillis
                    isLoaded = true
                }
            }

            if (!isLoaded) return@setContent

            val isDark = remember(refreshKey) { context.isDarkAppTheme }
            val isAmoled = remember(refreshKey) { context.useAmoledMode }
            val isDynamic = remember(refreshKey) { context.useMaterialYou }

            HardReminderTheme(
                darkTheme = isDark,
                amoled = isAmoled,
                dynamicColor = isDynamic
            ) {
                // Track scroll for animated FAB
                val scrollState = rememberScrollState()
                val isScrolling by remember { derivedStateOf { scrollState.isScrollInProgress } }

                Scaffold(
                    modifier = Modifier
                        .scale(1f - backProgress * 0.05f)
                        .alpha(1f - backProgress * 0.3f),
                    topBar = {
                        TopAppBar(
                            title = { Text(if (isEditing) "Edit Reminder" else "New Reminder") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    },
                    floatingActionButton = {
                        ExtendedFloatingActionButton(
                            expanded = !isScrolling,
                            icon = { Icon(Icons.Default.Save, contentDescription = null) },
                            text = { Text("Save Reminder") },
                            onClick = {
                                if (title.isBlank()) {
                                    Toast.makeText(context, "Title required", Toast.LENGTH_SHORT).show()
                                    return@ExtendedFloatingActionButton
                                }

                                if (repeatType == RepeatType.NONE && calendar.timeInMillis <= System.currentTimeMillis()) {
                                    Toast.makeText(context, "Please select a future time", Toast.LENGTH_SHORT).show()
                                    return@ExtendedFloatingActionButton
                                }

                                val repeatData = when (repeatType) {
                                    RepeatType.WEEKLY -> {
                                        val days = selectedDays.toList().mapIndexedNotNull { index, selected ->
                                            if (selected) (index + 1).toString() else null
                                        }
                                        if (days.isEmpty()) {
                                            Toast.makeText(context, "Select at least one day", Toast.LENGTH_SHORT).show()
                                            return@ExtendedFloatingActionButton
                                        }
                                        days.joinToString(",")
                                    }
                                    RepeatType.CUSTOM -> {
                                        val intervalDays = customInterval.toIntOrNull()
                                        if (intervalDays == null || intervalDays <= 0) {
                                            Toast.makeText(context, "Enter a valid number of days", Toast.LENGTH_SHORT).show()
                                            return@ExtendedFloatingActionButton
                                        }
                                        customInterval
                                    }
                                    else -> ""
                                }

                                val priorMinutes = AppSettings.PRIOR_OPTIONS.getOrNull(priorIndex)?.first ?: 0

                                val reminder = Reminder(
                                    id = if (isEditing) editingReminderId else 0,
                                    title = title.trim(),
                                    message = message.trim(),
                                    triggerTimeMillis = calendar.timeInMillis,
                                    isEnabled = true,
                                    repeatType = repeatType,
                                    repeatData = repeatData,
                                    soundEnabled = soundEnabled,
                                    vibrationEnabled = vibrationEnabled,
                                    priorNotifyMinutes = priorMinutes,
                                    createdAt = existingReminder?.createdAt ?: System.currentTimeMillis()
                                )

                                lifecycleScope.launch {
                                    if (isEditing) {
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
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = MaterialTheme.shapes.large
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(scrollState)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Title") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium
                        )

                        OutlinedTextField(
                            value = message,
                            onValueChange = { message = it },
                            label = { Text("Message (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        )

                        // Date & Time Card
                        SectionCard(
                            title = "Date & Time",
                            icon = Icons.Default.DateRange
                        ) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(
                                        onClick = { showDatePicker = true },
                                        modifier = Modifier.weight(1f),
                                        shape = MaterialTheme.shapes.medium
                                    ) {
                                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        val df = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                                        Text(df.format(Date(triggerTime)))
                                    }

                                    FilledTonalButton(
                                        onClick = { showTimePicker = true },
                                        modifier = Modifier.weight(1f),
                                        shape = MaterialTheme.shapes.medium
                                    ) {
                                        Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        val tf = if (context.use24HourFormat) {
                                            SimpleDateFormat("HH:mm", Locale.getDefault())
                                        } else {
                                            SimpleDateFormat("hh:mm a", Locale.getDefault())
                                        }
                                        Text(tf.format(Date(triggerTime)))
                                    }
                                }
                        }

                        // M3 DatePickerDialog
                        if (showDatePicker) {
                            val datePickerState = rememberDatePickerState(
                                initialSelectedDateMillis = triggerTime
                            )
                            DatePickerDialog(
                                onDismissRequest = { showDatePicker = false },
                                confirmButton = {
                                    TextButton(onClick = {
                                        datePickerState.selectedDateMillis?.let { millis ->
                                            val selected = Calendar.getInstance().apply { timeInMillis = millis }
                                            calendar.set(Calendar.YEAR, selected.get(Calendar.YEAR))
                                            calendar.set(Calendar.MONTH, selected.get(Calendar.MONTH))
                                            calendar.set(Calendar.DAY_OF_MONTH, selected.get(Calendar.DAY_OF_MONTH))
                                            triggerTime = calendar.timeInMillis
                                        }
                                        showDatePicker = false
                                    }) {
                                        Text("OK")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDatePicker = false }) {
                                        Text("Cancel")
                                    }
                                }
                            ) {
                                DatePicker(state = datePickerState)
                            }
                        }

                        // M3 TimePickerDialog
                        if (showTimePicker) {
                            val timePickerState = rememberTimePickerState(
                                initialHour = calendar.get(Calendar.HOUR_OF_DAY),
                                initialMinute = calendar.get(Calendar.MINUTE),
                                is24Hour = context.use24HourFormat
                            )
                            AlertDialog(
                                onDismissRequest = { showTimePicker = false },
                                title = { Text("Select time") },
                                text = {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        TimePicker(state = timePickerState)
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        calendar.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                                        calendar.set(Calendar.MINUTE, timePickerState.minute)
                                        calendar.set(Calendar.SECOND, 0)
                                        calendar.set(Calendar.MILLISECOND, 0)
                                        triggerTime = calendar.timeInMillis
                                        showTimePicker = false
                                    }) {
                                        Text("OK")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showTimePicker = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }

                        // Repeat Card
                        SectionCard(
                            title = "Repeat",
                            icon = Icons.Default.Repeat
                        ) {
                                Spacer(modifier = Modifier.height(8.dp))
                                var repeatMenuExpanded by remember { mutableStateOf(false) }
                                val repeatOptions = arrayOf("No Repeat", "Daily", "Weekdays (Mon-Fri)", "Weekends (Sat-Sun)", "Specific Days", "Custom Interval")
                                val selectedRepeatOption = when(repeatType) {
                                    RepeatType.NONE -> 0
                                    RepeatType.DAILY -> 1
                                    RepeatType.WEEKDAYS -> 2
                                    RepeatType.WEEKENDS -> 3
                                    RepeatType.WEEKLY -> 4
                                    RepeatType.CUSTOM -> 5
                                }

                                ExposedDropdownMenuBox(
                                    expanded = repeatMenuExpanded,
                                    onExpandedChange = { repeatMenuExpanded = !repeatMenuExpanded }
                                ) {
                                    OutlinedTextField(
                                        value = repeatOptions[selectedRepeatOption],
                                        onValueChange = {},
                                        readOnly = true,
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = repeatMenuExpanded) },
                                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                        shape = MaterialTheme.shapes.medium
                                    )
                                    ExposedDropdownMenu(
                                        expanded = repeatMenuExpanded,
                                        onDismissRequest = { repeatMenuExpanded = false }
                                    ) {
                                        repeatOptions.forEachIndexed { index, selectionOption ->
                                            DropdownMenuItem(
                                                text = { Text(selectionOption) },
                                                onClick = {
                                                    repeatType = when(index) {
                                                        0 -> RepeatType.NONE
                                                        1 -> RepeatType.DAILY
                                                        2 -> RepeatType.WEEKDAYS
                                                        3 -> RepeatType.WEEKENDS
                                                        4 -> RepeatType.WEEKLY
                                                        else -> RepeatType.CUSTOM
                                                    }
                                                    repeatMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                if (repeatType == RepeatType.WEEKLY) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        val dayNames = arrayOf("M", "T", "W", "T", "F", "S", "S")
                                        dayNames.forEachIndexed { index, day ->
                                            FilterChip(
                                                selected = selectedDays[index],
                                                onClick = {
                                                    val newDays = selectedDays.clone()
                                                    newDays[index] = !newDays[index]
                                                    selectedDays = newDays
                                                },
                                                label = { Text(day) },
                                                shape = RoundedCornerShape(50)
                                            )
                                        }
                                    }
                                } else if (repeatType == RepeatType.CUSTOM) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedTextField(
                                        value = customInterval,
                                        onValueChange = { customInterval = it },
                                        label = { Text("Repeat every X days") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = MaterialTheme.shapes.medium
                                    )
                                }
                        }

                        // Prior Notification Card
                        SectionCard(
                            title = "Remind Before",
                            icon = Icons.Default.Notifications
                        ) {
                                Spacer(modifier = Modifier.height(8.dp))
                                var priorMenuExpanded by remember { mutableStateOf(false) }

                                ExposedDropdownMenuBox(
                                    expanded = priorMenuExpanded,
                                    onExpandedChange = { priorMenuExpanded = !priorMenuExpanded }
                                ) {
                                    OutlinedTextField(
                                        value = AppSettings.PRIOR_OPTIONS.getOrNull(priorIndex)?.second ?: "None",
                                        onValueChange = {},
                                        readOnly = true,
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = priorMenuExpanded) },
                                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                        shape = MaterialTheme.shapes.medium
                                    )
                                    ExposedDropdownMenu(
                                        expanded = priorMenuExpanded,
                                        onDismissRequest = { priorMenuExpanded = false }
                                    ) {
                                        AppSettings.PRIOR_OPTIONS.forEachIndexed { index, pair ->
                                            DropdownMenuItem(
                                                text = { Text(pair.second) },
                                                onClick = {
                                                    priorIndex = index
                                                    priorMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                        }

                        // Sound & Vibration Card
                        SectionCard(
                            title = "Sound & Vibration",
                            icon = Icons.Default.VolumeUp
                        ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Sound", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                    Switch(checked = soundEnabled, onCheckedChange = { soundEnabled = it })
                                }
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Vibration", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                    Switch(checked = vibrationEnabled, onCheckedChange = { vibrationEnabled = it })
                                }
                        }

                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }
            content()
        }
    }
}
