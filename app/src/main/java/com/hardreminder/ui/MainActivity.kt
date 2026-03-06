package com.hardreminder.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.hardreminder.HardReminderApp
import com.hardreminder.alarm.AlarmScheduler
import com.hardreminder.data.Reminder
import com.hardreminder.ui.theme.HardReminderTheme
import com.hardreminder.data.AppSettings.isDarkAppTheme
import com.hardreminder.data.AppSettings.useAmoledMode
import com.hardreminder.data.AppSettings.useMaterialYou
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.app.ActivityOptionsCompat
import com.hardreminder.R

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op, we try our best */ }

    // Mutable state to force theme recomposition on resume
    private val themeRefreshTrigger = mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        // Increment to force recomposition of theme values from SharedPreferences
        themeRefreshTrigger.intValue++
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions()
        requestBatteryOptimizationExemption()

        val dao = (application as HardReminderApp).database.reminderDao()

        setContent {
            // Read the trigger — this forces recomposition when onResume fires
            val refreshKey = themeRefreshTrigger.intValue
            val reminders by dao.getAllReminders().collectAsState(initial = emptyList())
            var showDeleteDialog by remember { mutableStateOf<Reminder?>(null) }
            val context = LocalContext.current

            // Re-read theme values each time refreshKey changes
            val isDark = remember(refreshKey) { context.isDarkAppTheme }
            val isAmoled = remember(refreshKey) { context.useAmoledMode }
            val isDynamic = remember(refreshKey) { context.useMaterialYou }

            HardReminderTheme(
                darkTheme = isDark,
                amoled = isAmoled,
                dynamicColor = isDynamic
            ) {
                // Track scroll state for animated FAB
                val listState = rememberLazyListState()
                val isScrolling by remember { derivedStateOf { listState.isScrollInProgress } }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Column {
                                    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                                    val greeting = when {
                                        hour < 5  -> "Good night"
                                        hour < 12 -> "Good morning"
                                        hour < 17 -> "Good afternoon"
                                        hour < 21 -> "Good evening"
                                        else      -> "Good night"
                                    }
                                    Text(
                                        text = greeting,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val dateFmt = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
                                    Text(
                                        text = dateFmt.format(Date()),
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.alpha(0.7f)
                                    )
                                }
                            },
                            actions = {
                                IconButton(onClick = {
                    val opts = ActivityOptionsCompat.makeCustomAnimation(this@MainActivity, R.anim.slide_in_right, R.anim.slide_out_left)
                    startActivity(Intent(this@MainActivity, SettingsActivity::class.java), opts.toBundle())
                }) {
                                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface,
                                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    },
                    floatingActionButton = {
                        ExtendedFloatingActionButton(
                            onClick = {
                                val opts = ActivityOptionsCompat.makeCustomAnimation(this, R.anim.slide_in_right, R.anim.slide_out_left)
                                startActivity(Intent(this, AddEditReminderActivity::class.java), opts.toBundle())
                            },
                            expanded = !isScrolling,
                            icon = {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Add new reminder"
                                )
                            },
                            text = { Text("New Reminder") },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = MaterialTheme.shapes.large
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(innerPadding)
                    ) {
                        StatsSection(reminders)

                        if (reminders.isEmpty()) {
                            EmptyState(
                                onCreateClick = {
                                    val opts = ActivityOptionsCompat.makeCustomAnimation(this@MainActivity, R.anim.slide_in_right, R.anim.slide_out_left)
                                    startActivity(Intent(this@MainActivity, AddEditReminderActivity::class.java), opts.toBundle())
                                }
                            )
                        } else {
                            Text(
                                text = "UPCOMING",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp)
                            )
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(reminders, key = { it.id }) { reminder ->
                                    ReminderCard(
                                        reminder = reminder,
                                        onToggle = { enabled ->
                                            lifecycleScope.launch {
                                                dao.setEnabled(reminder.id, enabled)
                                                if (enabled) {
                                                    val updated = reminder.copy(isEnabled = true)
                                                    AlarmScheduler.scheduleAlarm(this@MainActivity, updated)
                                                    AlarmScheduler.schedulePriorAlarm(this@MainActivity, updated)
                                                } else {
                                                    AlarmScheduler.cancelAlarm(this@MainActivity, reminder.id)
                                                    AlarmScheduler.cancelPriorAlarm(this@MainActivity, reminder.id)
                                                }
                                            }
                                        },
                                        onClick = {
                                            val intent = Intent(this@MainActivity, AddEditReminderActivity::class.java).apply {
                                                putExtra("REMINDER_ID", reminder.id)
                                            }
                                            val opts = ActivityOptionsCompat.makeCustomAnimation(this@MainActivity, R.anim.slide_in_right, R.anim.slide_out_left)
                                            startActivity(intent, opts.toBundle())
                                        },
                                        onDelete = {
                                            showDeleteDialog = reminder
                                        }
                                    )
                                }
                                item { Spacer(modifier = Modifier.height(80.dp)) }
                            }
                        }
                    }
                }

                if (showDeleteDialog != null) {
                    val reminderToDelete = showDeleteDialog!!
                    AlertDialog(
                        onDismissRequest = { showDeleteDialog = null },
                        title = { Text("Delete Reminder") },
                        text = { Text("Delete \"${reminderToDelete.title}\"?") },
                        confirmButton = {
                            TextButton(onClick = {
                                lifecycleScope.launch {
                                    AlarmScheduler.cancelAlarm(this@MainActivity, reminderToDelete.id)
                                    AlarmScheduler.cancelPriorAlarm(this@MainActivity, reminderToDelete.id)
                                    dao.deleteReminder(reminderToDelete)
                                }
                                showDeleteDialog = null
                            }) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteDialog = null }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(android.app.AlarmManager::class.java)
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }

    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }
}

@Composable
fun StatsSection(reminders: List<Reminder>) {
    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val todayEnd = todayStart + 86_400_000L

    val active = reminders.count { it.isEnabled }
    val today = reminders.count {
        it.isEnabled && it.triggerTimeMillis in todayStart until todayEnd
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatCard(title = "Active", count = active)
        StatCard(title = "Today", count = today)
        StatCard(title = "Total", count = reminders.size)
    }
}

@Composable
fun RowScope.StatCard(title: String, count: Int) {
    Card(
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
fun EmptyState(onCreateClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No reminders yet",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Your day is clear — add one to get started",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        FilledTonalButton(
            onClick = onCreateClick,
            modifier = Modifier.padding(top = 24.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text("Create Reminder")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderCard(
    reminder: Reminder,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val alpha = if (reminder.isEnabled) 1.0f else 0.5f

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                if (reminder.message.isNotEmpty()) {
                    Text(
                        text = reminder.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                val fmt = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
                Text(
                    text = "${fmt.format(Date(reminder.triggerTimeMillis))} • ${reminder.repeatType.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
            Switch(
                checked = reminder.isEnabled,
                onCheckedChange = { onToggle(it) }
            )
        }
    }
}
