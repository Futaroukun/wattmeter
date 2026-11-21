package com.antigravity.wattmeter.data.model

data class BatterySnapshot(
    val currentNowMa: Double = 0.0,
    val voltageVolt: Double = 0.0,
    val watt: Double = 0.0,
    val smoothedWatt: Double = 0.0,
    val temperatureCelsius: Float = 0.0f,
    val levelPercent: Int = 0,
    val isCharging: Boolean = false,
    val status: String = "",
    val plugType: String = "",
    val health: String = "",
    val technology: String = "",
    val capacityDesignMah: Int = -1,
    val chargeCounterUah: Long = 0L,
    val currentDataSource: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
