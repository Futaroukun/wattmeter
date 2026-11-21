package com.antigravity.wattmeter.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "charging_sessions")
data class ChargingSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val startTime: Long,
    val endTime: Long,
    val startLevel: Int,
    val endLevel: Int,
    val peakWatt: Double,
    val avgWatt: Double,
    val minWatt: Double,
    val peakCurrentMa: Double,
    val avgCurrentMa: Double,
    val maxTemperature: Float,
    val plugType: String,
    val energyWh: Double
)
