package com.antigravity.wattmeter.data.repository

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.antigravity.wattmeter.data.model.BatterySnapshot
import com.antigravity.wattmeter.data.model.DiagnosticInfo
import com.antigravity.wattmeter.data.model.UnitMultiplier
import com.antigravity.wattmeter.data.model.UserSettings
import java.io.File
import kotlin.math.abs

class BatteryReader(private val context: Context) {
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

    private val sysfsCurrentPaths = listOf(
        "/sys/class/power_supply/battery/current_now",
        "/sys/class/power_supply/battery/batt_current_now",
        "/sys/class/power_supply/battery/voltage_now",
        "/sys/class/power_supply/bms/current_now"
    )

    fun readBattery(settings: UserSettings): Pair<BatterySnapshot, DiagnosticInfo> {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val rawVoltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val voltageVolt = if (rawVoltage > 0) {
            rawVoltage / 1000.0
        } else {
            readSysfsVoltage()
        }

        val rawTemp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val temperatureCelsius = rawTemp / 10.0f

        val rawLevel = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val levelPercent = if (rawLevel >= 0 && scale > 0) {
            (rawLevel * 100) / scale
        } else {
            rawLevel.coerceAtLeast(0)
        }

        val statusInt = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val (statusString, isCharging) = parseStatus(statusInt)

        val pluggedInt = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val plugType = parsePlugType(pluggedInt)

        val healthInt = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
            ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
        val health = parseHealth(healthInt)

        val technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown"

        val chargeCounterUah = batteryManager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: 0L
        val capacityDesignMah = readDesignCapacity()

        val currentNowRaw = batteryManager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0L
        val currentAverageRaw = batteryManager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE) ?: 0L

        val sysfsChecked = mutableListOf<Pair<String, String>>()
        var selectedRawCurrent = 0L
        var dataSource = "None"

        if (isValidCurrent(currentNowRaw)) {
            selectedRawCurrent = currentNowRaw
            dataSource = "BatteryManager.BATTERY_PROPERTY_CURRENT_NOW"
        } else if (isValidCurrent(currentAverageRaw)) {
            selectedRawCurrent = currentAverageRaw
            dataSource = "BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE"
        }

        for (path in sysfsCurrentPaths) {
            val content = readSysfsFile(path)
            sysfsChecked.add(Pair(path, content ?: "Unavailable / Unreadable"))
            if (selectedRawCurrent == 0L && path != "/sys/class/power_supply/battery/voltage_now") {
                val parsedLong = content?.toLongOrNull()
                if (parsedLong != null && isValidCurrent(parsedLong)) {
                    selectedRawCurrent = parsedLong
                    dataSource = "sysfs ($path)"
                }
            }
        }

        val (currentMa, detectedUnit) = convertToMilliamperes(selectedRawCurrent, settings.unitMultiplier)

        val finalCurrentMa = if (settings.invertCurrentSign) {
            -currentMa
        } else {
            currentMa
        }

        val watt = (abs(finalCurrentMa) / 1000.0) * voltageVolt

        val isZeroCurrentWarning = (selectedRawCurrent == 0L || !isValidCurrent(selectedRawCurrent))

        val explanation = if (isZeroCurrentWarning) {
            "Zero current detected. BatteryManager CURRENT_NOW: $currentNowRaw, CURRENT_AVERAGE: $currentAverageRaw. Sysfs paths checked: ${sysfsChecked.joinToString { "${it.first}='${it.second}'" }}. The OEM kernel or device firmware may restrict battery current access."
        } else {
            "Successfully read from $dataSource ($detectedUnit)."
        }

        val snapshot = BatterySnapshot(
            currentNowMa = finalCurrentMa,
            voltageVolt = voltageVolt,
            watt = watt,
            smoothedWatt = watt,
            temperatureCelsius = temperatureCelsius,
            levelPercent = levelPercent,
            isCharging = isCharging,
            status = statusString,
            plugType = plugType,
            health = health,
            technology = technology,
            capacityDesignMah = capacityDesignMah,
            chargeCounterUah = chargeCounterUah,
            currentDataSource = dataSource,
            timestamp = System.currentTimeMillis()
        )

        val diagnostic = DiagnosticInfo(
            currentNowRaw = currentNowRaw,
            currentAverageRaw = currentAverageRaw,
            sysfsChecked = sysfsChecked,
            detectedUnit = detectedUnit,
            signReversed = settings.invertCurrentSign,
            isZeroCurrentWarning = isZeroCurrentWarning,
            explanation = explanation
        )

        return Pair(snapshot, diagnostic)
    }

    private fun isValidCurrent(value: Long): Boolean {
        return value != 0L && value != Long.MIN_VALUE && value != Long.MAX_VALUE && abs(value) < 100_000_000L
    }

    private fun convertToMilliamperes(raw: Long, multiplier: UnitMultiplier): Pair<Double, String> {
        return when (multiplier) {
            UnitMultiplier.MICRO_TO_MILLI -> {
                Pair(raw / 1000.0, "µA (manual)")
            }
            UnitMultiplier.ONE -> {
                Pair(raw.toDouble(), "mA (manual)")
            }
            UnitMultiplier.AUTO -> {
                val absVal = abs(raw)
                when {
                    absVal > 10000L -> Pair(raw / 1000.0, "µA (auto)")
                    absVal > 0L -> Pair(raw.toDouble(), "mA (auto)")
                    else -> Pair(0.0, "None")
                }
            }
        }
    }

    private fun readSysfsVoltage(): Double {
        val path = "/sys/class/power_supply/battery/voltage_now"
        val content = readSysfsFile(path)
        val raw = content?.toLongOrNull() ?: return 0.0
        return when {
            raw > 1_000_000L -> raw / 1_000_000.0
            raw > 1000L -> raw / 1000.0
            else -> raw.toDouble()
        }
    }

    private fun readSysfsFile(path: String): String? {
        return try {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                file.readText().trim()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseStatus(status: Int): Pair<String, Boolean> {
        return when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> Pair("Charging", true)
            BatteryManager.BATTERY_STATUS_DISCHARGING -> Pair("Discharging", false)
            BatteryManager.BATTERY_STATUS_FULL -> Pair("Full", true)
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> Pair("Not Charging", false)
            else -> Pair("Unknown", false)
        }
    }

    private fun parsePlugType(plugged: Int): String {
        return when {
            plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> "AC"
            plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> "USB"
            plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> "Wireless"
            plugged != 0 -> "Plugged"
            else -> "Battery"
        }
    }

    private fun parseHealth(health: Int): String {
        return when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }
    }

    private fun readDesignCapacity(): Int {
        return try {
            val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
            val powerProfile = powerProfileClass.getConstructor(Context::class.java).newInstance(context)
            val capacity = powerProfileClass.getMethod("getBatteryCapacity").invoke(powerProfile) as? Double
            capacity?.toInt() ?: -1
        } catch (e: Exception) {
            -1
        }
    }
}
