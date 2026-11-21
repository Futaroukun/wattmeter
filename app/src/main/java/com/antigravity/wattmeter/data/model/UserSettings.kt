package com.antigravity.wattmeter.data.model

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    OLED
}

enum class UnitMultiplier {
    AUTO,
    ONE,
    MICRO_TO_MILLI
}

enum class NotificationMetric {
    WATT,
    AMPERE,
    VOLT,
    TEMP,
    PERCENT
}

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val pollingIntervalMs: Long = 1000L,
    val unitMultiplier: UnitMultiplier = UnitMultiplier.AUTO,
    val invertCurrentSign: Boolean = false,
    val movingAvgWindow: Int = 5,
    val notificationMetric: NotificationMetric = NotificationMetric.WATT,
    val alarmFullBattery: Boolean = true,
    val alarmLevelThreshold: Int = 80,
    val alarmTempThreshold: Float = 42.0f,
    val autoStartOnBoot: Boolean = false,
    val autoStartOnPlug: Boolean = true
)
