package com.hardreminder.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val message: String = "",
    val triggerTimeMillis: Long,        // Next scheduled trigger time
    val createdAt: Long = System.currentTimeMillis(),
    val isEnabled: Boolean = true,
    val repeatType: RepeatType = RepeatType.NONE,
    // For WEEKLY: comma-separated day numbers (1=Mon, 7=Sun), e.g. "1,3,5"
    // For CUSTOM: interval in days, e.g. "3" for every 3 days
    val repeatData: String = "",
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val priorNotifyMinutes: Int = 0  // 0 = disabled, >0 = notify this many minutes before
)

class Converters {
    @TypeConverter
    fun fromRepeatType(value: RepeatType): String = value.name

    @TypeConverter
    fun toRepeatType(value: String): RepeatType = RepeatType.valueOf(value)
}
