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
    val alarmFullBattery: Boolean = false,
    val alarmLevelEnabled: Boolean = false,
    val alarmLevelThreshold: Int = 80,
    val alarmTempEnabled: Boolean = false,
    val alarmTempThreshold: Float = 42.0f,
    val autoStartOnBoot: Boolean = false,
    val autoStartOnPlug: Boolean = true,
    val estimateCurrentIfZero: Boolean = true,
    val chargerWattageRating: Int = 18
)
