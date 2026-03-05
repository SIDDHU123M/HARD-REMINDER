package com.hardreminder.ui

import android.graphics.Color
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hardreminder.R
import com.hardreminder.data.Reminder
import com.hardreminder.data.AppSettings
import com.hardreminder.data.RepeatType
import com.hardreminder.databinding.ItemReminderBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReminderAdapter(
    private val onToggle: (Reminder, Boolean) -> Unit,
    private val onEdit: (Reminder) -> Unit,
    private val onDelete: (Reminder) -> Unit
) : ListAdapter<Reminder, ReminderAdapter.ViewHolder>(DiffCallback) {

    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEE, MMM dd yyyy", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReminderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemReminderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(reminder: Reminder) {
            val context = binding.root.context

            // ── Time & date ─────────────────────────────────
            binding.textTimeLarge.text = timeFormat.format(Date(reminder.triggerTimeMillis))
            binding.textDateSmall.text = dateFormat.format(Date(reminder.triggerTimeMillis))

            // ── Title & message ─────────────────────────────
            binding.textTitle.text = reminder.title
            binding.textMessage.text = reminder.message.ifEmpty { "No message" }

            // ── Toggle switch (detach first to avoid spurious callbacks) ──
            binding.switchEnabled.setOnCheckedChangeListener(null)
            binding.switchEnabled.isChecked = reminder.isEnabled

            // ── Tag pills ───────────────────────────────────
            binding.tagRepeat.text = getRepeatLabel(reminder)

            if (reminder.priorNotifyMinutes > 0) {
                binding.tagPrior.visibility = View.VISIBLE
                binding.tagPrior.text = "⏰ ${AppSettings.getPriorLabel(reminder.priorNotifyMinutes)}"
            } else {
                binding.tagPrior.visibility = View.GONE
            }

            // ── Left accent strip colour ────────────────────
            val isPast = reminder.triggerTimeMillis < System.currentTimeMillis()
                    && reminder.repeatType == RepeatType.NONE

            if (!reminder.isEnabled) {
                // Disabled: grey strip
                binding.accentStrip.setBackgroundColor(
                    ContextCompat.getColor(context, R.color.past_reminder)
                )
            } else if (isPast) {
                // Fired (past one-time): muted strip
                binding.accentStrip.setBackgroundColor(
                    ContextCompat.getColor(context, R.color.past_reminder)
                )
            } else {
                // Active: colorPrimary strip
                val tv = TypedValue()
                context.theme.resolveAttribute(
                    com.google.android.material.R.attr.colorPrimary, tv, true
                )
                binding.accentStrip.setBackgroundColor(tv.data)
            }

            // ── Time text colour for past reminders ─────────
            if (isPast && reminder.isEnabled) {
                binding.textTimeLarge.setTextColor(
                    ContextCompat.getColor(context, R.color.past_reminder)
                )
                binding.textTimeLarge.text = "Fired · ${timeFormat.format(Date(reminder.triggerTimeMillis))}"
            } else {
                val tv = TypedValue()
                context.theme.resolveAttribute(
                    com.google.android.material.R.attr.colorPrimary, tv, true
                )
                binding.textTimeLarge.setTextColor(tv.data)
            }

            // ── Enabled/disabled visual dimming ─────────────
            binding.cardContent.alpha = if (reminder.isEnabled) 1.0f else 0.5f

            // ── Listeners ───────────────────────────────────
            binding.switchEnabled.setOnCheckedChangeListener { _, checked ->
                onToggle(reminder, checked)
            }
            binding.root.setOnClickListener { onEdit(reminder) }
            binding.root.setOnLongClickListener {
                onDelete(reminder)
                true
            }
        }

        private fun getRepeatLabel(reminder: Reminder): String {
            return when (reminder.repeatType) {
                RepeatType.NONE -> "One time"
                RepeatType.DAILY -> "Daily"
                RepeatType.WEEKDAYS -> "Weekdays"
                RepeatType.WEEKENDS -> "Weekends"
                RepeatType.WEEKLY -> {
                    val dayNames = reminder.repeatData.split(",").mapNotNull { dayStr ->
                        when (dayStr.trim().toIntOrNull()) {
                            1 -> "Mon"; 2 -> "Tue"; 3 -> "Wed"; 4 -> "Thu"
                            5 -> "Fri"; 6 -> "Sat"; 7 -> "Sun"; else -> null
                        }
                    }
                    dayNames.joinToString(", ")
                }
                RepeatType.CUSTOM -> {
                    val days = reminder.repeatData.toIntOrNull() ?: 1
                    "Every $days day${if (days > 1) "s" else ""}"
                }
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<Reminder>() {
        override fun areItemsTheSame(old: Reminder, new: Reminder) = old.id == new.id
        override fun areContentsTheSame(old: Reminder, new: Reminder) = old == new
    }
}
