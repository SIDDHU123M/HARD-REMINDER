@file:OptIn(ExperimentalMaterial3Api::class)

package com.hardreminder.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import com.hardreminder.R
import com.hardreminder.data.AppSettings
import com.hardreminder.data.AppSettings.autoDeleteFired
import com.hardreminder.data.AppSettings.defaultPriorMinutes
import com.hardreminder.data.AppSettings.defaultSound
import com.hardreminder.data.AppSettings.defaultVibration
import com.hardreminder.data.AppSettings.flashScreenOnAlarm
import com.hardreminder.data.AppSettings.isDarkAppTheme
import com.hardreminder.data.AppSettings.showOngoingNotification
import com.hardreminder.data.AppSettings.snoozeMinutes
import com.hardreminder.data.AppSettings.startOnBoot
import com.hardreminder.data.AppSettings.themeMode
import com.hardreminder.data.AppSettings.use24HourFormat
import com.hardreminder.data.AppSettings.useAmoledMode
import com.hardreminder.data.AppSettings.useMaterialYou
import com.hardreminder.service.ReminderForegroundService
import com.hardreminder.ui.theme.HardReminderTheme
import kotlinx.coroutines.CancellationException

class SettingsActivity : ComponentActivity() {

    private val themeRefreshTrigger = mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        themeRefreshTrigger.intValue++
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            var soundState by remember { mutableStateOf(defaultSound) }
            var vibrationState by remember { mutableStateOf(defaultVibration) }
            var use24hState by remember { mutableStateOf(use24HourFormat) }
            var autoDeleteState by remember { mutableStateOf(autoDeleteFired) }
            var startOnBootState by remember { mutableStateOf(startOnBoot) }
            var ongoingNotifState by remember { mutableStateOf(showOngoingNotification) }
            var flashScreenState by remember { mutableStateOf(flashScreenOnAlarm) }

            var amoledState by remember { mutableStateOf(useAmoledMode) }
            var themeState by remember { mutableStateOf(themeMode) }
            var materialYouState by remember { mutableStateOf(useMaterialYou) }

            var snoozeState by remember { mutableStateOf(snoozeMinutes) }
            var priorState by remember { mutableStateOf(defaultPriorMinutes) }

            var batteryExempt by remember {
                mutableStateOf(
                    getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
                )
            }

            val isDark = remember(refreshKey) { context.isDarkAppTheme }
            val isAmoled = remember(refreshKey) { context.useAmoledMode }
            val isDynamic = remember(refreshKey) { context.useMaterialYou }

            HardReminderTheme(
                darkTheme = isDark,
                amoled = isAmoled,
                dynamicColor = isDynamic
            ) {
                Scaffold(
                    modifier = Modifier
                        .scale(1f - backProgress * 0.05f)
                        .alpha(1f - backProgress * 0.3f),
                    topBar = {
                        TopAppBar(
                            title = { Text("Settings") },
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
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(innerPadding)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Appearance Section
                        SectionHeader(
                            title = "Appearance",
                            icon = Icons.Default.Palette
                        )

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                    Text("Theme", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    val themeOptions = listOf(
                                        "System" to AppSettings.THEME_SYSTEM,
                                        "Light" to AppSettings.THEME_LIGHT,
                                        "Dark" to AppSettings.THEME_DARK
                                    )
                                    SingleChoiceButtonGroup(
                                        items = themeOptions.map { it.first },
                                        selectedIndex = themeOptions.indexOfFirst { it.second == themeState }.coerceAtLeast(0),
                                        onSelected = { index ->
                                            val mode = themeOptions[index].second
                                            themeState = mode
                                            themeMode = mode
                                            recreate()
                                        }
                                    )
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

                                SettingSwitchRow("AMOLED Dark Mode", "True black background for OLED screens", amoledState) {
                                    amoledState = it; useAmoledMode = it; recreate()
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

                                SettingSwitchRow("Material You", "Use dynamic colors matching wallpaper", materialYouState) {
                                    materialYouState = it; useMaterialYou = it; recreate()
                                }
                            }
                        }

                        // Defaults & Automation Section
                        SectionHeader(
                            title = "Defaults & Automation",
                            icon = Icons.Default.Build
                        )
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                SettingSwitchRow("Default Sound", "Play sound for new reminders by default", soundState) { soundState = it; defaultSound = it }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                SettingSwitchRow("Default Vibration", "Vibrate for new reminders by default", vibrationState) { vibrationState = it; defaultVibration = it }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                                    Text("Default Prior Notification", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    val priorOptions = AppSettings.PRIOR_OPTIONS
                                    val selectedPriorIndex = priorOptions.indexOfFirst { it.first == priorState }.coerceAtLeast(0)
                                    val priorChunks = priorOptions.chunked(3)
                                    var globalPriorIndex = 0
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        priorChunks.forEach { chunk ->
                                            val startIdx = globalPriorIndex
                                            SingleChoiceButtonGroup(
                                                items = chunk.map {
                                                    it.second.replace(" before", "").replace(" notification", "").replace("No prior ", "None")
                                                },
                                                selectedIndex = if (selectedPriorIndex in startIdx until startIdx + chunk.size) selectedPriorIndex - startIdx else -1,
                                                onSelected = { index ->
                                                    val option = chunk[index]
                                                    priorState = option.first
                                                    defaultPriorMinutes = option.first
                                                }
                                            )
                                            globalPriorIndex += chunk.size
                                        }
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                                    Text("Snooze Duration", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    val snoozeOptions = AppSettings.SNOOZE_OPTIONS
                                    val selectedSnoozeIndex = snoozeOptions.indexOfFirst { it.first == snoozeState }.coerceAtLeast(0)
                                    val snoozeChunks = snoozeOptions.chunked(4)
                                    var globalSnoozeIndex = 0
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        snoozeChunks.forEach { chunk ->
                                            val startIdx = globalSnoozeIndex
                                            SingleChoiceButtonGroup(
                                                items = chunk.map {
                                                    it.second.replace(" minutes", "m").replace(" minute", "m").replace(" hour", "h")
                                                },
                                                selectedIndex = if (selectedSnoozeIndex in startIdx until startIdx + chunk.size) selectedSnoozeIndex - startIdx else -1,
                                                onSelected = { index ->
                                                    val option = chunk[index]
                                                    snoozeState = option.first
                                                    snoozeMinutes = option.first
                                                }
                                            )
                                            globalSnoozeIndex += chunk.size
                                        }
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                SettingSwitchRow("Auto-delete Fired Reminders", "Delete reminders automatically once dismissed", autoDeleteState) { autoDeleteState = it; autoDeleteFired = it }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                SettingSwitchRow("Use 24-hour Format", "Use 24h format for time picker", use24hState) { use24hState = it; use24HourFormat = it }
                            }
                        }

                        // System Requirements Section
                        SectionHeader(
                            title = "System Requirements",
                            icon = Icons.Default.BatteryFull
                        )
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (!batteryExempt) {
                                                startActivity(
                                                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                        data = Uri.parse("package:$packageName")
                                                    })
                                            }
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Battery Optimization", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                        Text(
                                            text = if (batteryExempt) "Unrestricted" else "Restricted (tap to fix)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (batteryExempt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                        )
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                SettingSwitchRow("Alarm Wake Screen", "Turn screen on when alarm fires", flashScreenState) { flashScreenState = it; flashScreenOnAlarm = it }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                SettingSwitchRow("Start on Boot", "Reschedule alarms when device restarts", startOnBootState) { startOnBootState = it; startOnBoot = it }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                SettingSwitchRow("Ongoing Notification", "Keep notification to prevent Android from killing app", ongoingNotifState) {
                                    ongoingNotifState = it
                                    showOngoingNotification = it
                                    if (it) {
                                        startForegroundService(Intent(this@SettingsActivity, ReminderForegroundService::class.java))
                                    } else {
                                        stopService(Intent(this@SettingsActivity, ReminderForegroundService::class.java))
                                    }
                                }
                            }
                        }

                        // App Info Section
                        SectionHeader(
                            title = "App Info",
                            icon = Icons.Default.Info
                        )
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val options = ActivityOptionsCompat.makeCustomAnimation(
                                        this@SettingsActivity,
                                        R.anim.slide_in_right,
                                        R.anim.slide_out_left
                                    )
                                    startActivity(Intent(this@SettingsActivity, AboutActivity::class.java), options.toBundle())
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(16.dp))
                                    Text("About Hard Reminder", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SectionHeader(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SingleChoiceButtonGroup(
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        items.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selectedIndex,
                onClick = { onSelected(index) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = items.size),
                icon = {}
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
