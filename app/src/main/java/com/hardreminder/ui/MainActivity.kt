package com.hardreminder.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.hardreminder.HardReminderApp
import com.hardreminder.R
import com.hardreminder.alarm.AlarmScheduler
import com.hardreminder.data.Reminder
import com.hardreminder.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ReminderAdapter

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op, we try our best */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeHelper.applyActivityTheme(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupHeader()
        setupRecyclerView()
        setupFab()
        requestPermissions()
        requestBatteryOptimizationExemption()
        observeReminders()
    }

    override fun onResume() {
        super.onResume()
        updateGreeting()
    }

    // ── Header ──────────────────────────────────────────────

    private fun setupHeader() {
        updateGreeting()

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    private fun updateGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val (greeting, emoji) = when {
            hour < 5  -> "Good night" to "🌙"
            hour < 12 -> "Good morning" to "☀️"
            hour < 17 -> "Good afternoon" to "🌤️"
            hour < 21 -> "Good evening" to "🌆"
            else      -> "Good night" to "🌙"
        }
        binding.textGreeting.text = "$greeting $emoji"

        val dateFmt = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
        binding.textDate.text = dateFmt.format(Date())
    }

    // ── Stats ───────────────────────────────────────────────

    private fun updateStats(reminders: List<Reminder>) {
        val now = System.currentTimeMillis()
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

        binding.statActiveCount.text = active.toString()
        binding.statTodayCount.text = today.toString()
        binding.statTotalCount.text = reminders.size.toString()
    }

    // ── RecyclerView ────────────────────────────────────────

    private fun setupRecyclerView() {
        val dao = (application as HardReminderApp).database.reminderDao()

        adapter = ReminderAdapter(
            onToggle = { reminder, enabled ->
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
            onEdit = { reminder ->
                val intent = Intent(this, AddEditReminderActivity::class.java).apply {
                    putExtra("REMINDER_ID", reminder.id)
                }
                startActivity(intent)
            },
            onDelete = { reminder ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Delete Reminder")
                    .setMessage("Delete \"${reminder.title}\"?")
                    .setPositiveButton("Delete") { _, _ ->
                        lifecycleScope.launch {
                            AlarmScheduler.cancelAlarm(this@MainActivity, reminder.id)
                            AlarmScheduler.cancelPriorAlarm(this@MainActivity, reminder.id)
                            dao.deleteReminder(reminder)
                            Snackbar.make(binding.root, "Reminder deleted", Snackbar.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.itemAnimator = DefaultItemAnimator().apply {
            addDuration = 200
            removeDuration = 200
            changeDuration = 150
        }

        // Swipe to delete
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val reminder = adapter.currentList[position]
                lifecycleScope.launch {
                    AlarmScheduler.cancelAlarm(this@MainActivity, reminder.id)
                    AlarmScheduler.cancelPriorAlarm(this@MainActivity, reminder.id)
                    dao.deleteReminder(reminder)
                    Snackbar.make(binding.root, "\"${reminder.title}\" deleted", Snackbar.LENGTH_LONG)
                        .setAction("Undo") {
                            lifecycleScope.launch {
                                dao.insertReminder(reminder)
                                if (reminder.isEnabled) {
                                    AlarmScheduler.scheduleAlarm(this@MainActivity, reminder)
                                    AlarmScheduler.schedulePriorAlarm(this@MainActivity, reminder)
                                }
                            }
                        }.show()
                }
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.recyclerView)
    }

    // ── FAB ─────────────────────────────────────────────────

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddEditReminderActivity::class.java))
        }

        // Also wire the empty-state button
        binding.btnCreateReminder.setOnClickListener {
            startActivity(Intent(this, AddEditReminderActivity::class.java))
        }

        // Shrink/extend FAB on scroll
        binding.nestedScroll.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (scrollY > oldScrollY + 12) {
                binding.fabAdd.shrink()
            } else if (scrollY < oldScrollY - 12) {
                binding.fabAdd.extend()
            }
        }
    }

    // ── Observe ─────────────────────────────────────────────

    private fun observeReminders() {
        val dao = (application as HardReminderApp).database.reminderDao()
        lifecycleScope.launch {
            dao.getAllReminders().collectLatest { reminders ->
                adapter.submitList(reminders)
                updateStats(reminders)

                val isEmpty = reminders.isEmpty()
                binding.emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
                binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
                binding.sectionHeader.visibility = if (isEmpty) View.GONE else View.VISIBLE
            }
        }
    }

    // ── Permissions ─────────────────────────────────────────

    private fun requestPermissions() {
        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Exact alarm permission (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(android.app.AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }

    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Battery Optimization")
                .setMessage("For reliable reminders, please disable battery optimization for this app.")
                .setPositiveButton("Settings") { _, _ ->
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                }
                .setNegativeButton("Later", null)
                .show()
        }
    }
}
