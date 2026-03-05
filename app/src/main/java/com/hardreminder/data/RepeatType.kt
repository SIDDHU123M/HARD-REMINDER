package com.hardreminder.data

enum class RepeatType {
    NONE,       // One-time reminder
    DAILY,      // Every day
    WEEKLY,     // Specific days of the week
    WEEKDAYS,   // Monday-Friday
    WEEKENDS,   // Saturday-Sunday
    CUSTOM      // Custom interval in days
}
