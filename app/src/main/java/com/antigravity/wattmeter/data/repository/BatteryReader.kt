package com.antigravity.wattmeter.data.repository

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import com.antigravity.wattmeter.data.model.BatterySnapshot
import com.antigravity.wattmeter.data.model.DiagnosticInfo
import com.antigravity.wattmeter.data.model.UnitMultiplier
import com.antigravity.wattmeter.data.model.UserSettings
import com.antigravity.wattmeter.util.VoltageNormalizer
import java.io.File
import kotlin.math.abs

class BatteryReader(private val context: Context) {
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

    private val sysfsCurrentPaths = listOf(
        "/sys/class/power_supply/battery/current_now",
        "/sys/class/power_supply/battery/batt_current_now",
        "/sys/class/power_supply/battery/current_avg",
        "/sys/class/power_supply/bms/current_now",
        "/sys/class/power_supply/usb/current_now",
        "/sys/class/power_supply/main/current_now",
        "/sys/class/power_supply/charger/current_now"
    )

    private val sysfsVoltagePaths = listOf(
        "/sys/class/power_supply/battery/voltage_now",
        "/sys/class/power_supply/battery/batt_vol",
        "/sys/class/power_supply/bms/voltage_now"
    )

    private var lastSysfsScanTime = 0L
    private var cachedSysfsChecked: List<Pair<String, String>> = emptyList()
    private var lastLevelCheckTime = 0L
    private var lastLevelPercent = -1
    private var empiricalCurrentMa = 0.0

    fun readBattery(settings: UserSettings): Pair<BatterySnapshot, DiagnosticInfo> {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val rawTemp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val temperatureCelsius = rawTemp / 10.0f

        val rawLevel = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val levelPercent = if (rawLevel >= 0 && scale > 0) {
            (rawLevel * 100) / scale
        } else {
            rawLevel.coerceAtLeast(0)
        }

        val rawVoltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)?.toLong() ?: 0L
        val voltageResult = VoltageNormalizer.resolve(
            rawVoltage,
            levelPercent,
            sysfsVoltagePaths.asSequence().mapNotNull { readSysfsFile(it)?.toLongOrNull() }
        )
        val voltageVolt = voltageResult.volt

        val statusInt = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val (statusString, isCharging) = parseStatus(statusInt)

        val pluggedInt = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val plugType = parsePlugType(pluggedInt)

        val healthInt = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
            ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
        val health = parseHealth(healthInt)

        val technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown"

        val chargeCounterUah = batteryManager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            ?: batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)?.toLong()
            ?: 0L
        val capacityDesignMah = readDesignCapacity()

        val curNowLong = batteryManager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0L
        val curNowInt = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)?.toLong() ?: 0L
        val currentNowRaw = if (isValidCurrent(curNowLong)) curNowLong else curNowInt

        val curAvgLong = batteryManager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE) ?: 0L
        val curAvgInt = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)?.toLong() ?: 0L
        val currentAverageRaw = if (isValidCurrent(curAvgLong)) curAvgLong else curAvgInt

        val maxCurrentFromIntent = intent?.getIntExtra("max_charging_current", -1)?.toLong() ?: -1L

        val sysfsChecked = mutableListOf<Pair<String, String>>()
        var selectedRawCurrent = 0L
        var dataSource = "None"
        var isEstimated = false

        if (isValidCurrent(currentNowRaw)) {
            selectedRawCurrent = currentNowRaw
            dataSource = "BatteryManager.BATTERY_PROPERTY_CURRENT_NOW"
        } else if (isValidCurrent(currentAverageRaw)) {
            selectedRawCurrent = currentAverageRaw
            dataSource = "BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE"
        } else if (isValidCurrent(maxCurrentFromIntent)) {
            selectedRawCurrent = maxCurrentFromIntent
            dataSource = "Intent.EXTRA_MAX_CHARGING_CURRENT"
        }

        val scanNow = System.currentTimeMillis()
        val needSysfsScan = selectedRawCurrent == 0L || scanNow - lastSysfsScanTime > SYSFS_SCAN_INTERVAL_MS
        if (needSysfsScan) {
            val scanned = mutableListOf<Pair<String, String>>()
            for (path in sysfsCurrentPaths) {
                val content = readSysfsFile(path)
                scanned.add(Pair(path, content ?: "Unavailable / Unreadable"))
                if (selectedRawCurrent == 0L) {
                    val parsedLong = content?.toLongOrNull()
                    if (parsedLong != null && isValidCurrent(parsedLong)) {
                        selectedRawCurrent = parsedLong
                        dataSource = "sysfs ($path)"
                    }
                }
            }
            cachedSysfsChecked = scanned
            lastSysfsScanTime = scanNow
        }
        sysfsChecked.addAll(cachedSysfsChecked)

        if (selectedRawCurrent == 0L && isCharging && settings.estimateCurrentIfZero) {
            val now = System.currentTimeMillis()
            if (lastLevelPercent in 0..99 && levelPercent > lastLevelPercent && lastLevelCheckTime > 0) {
                val elapsedSec = (now - lastLevelCheckTime) / 1000.0
                if (elapsedSec > 5.0) {
                    val capacity = if (capacityDesignMah > 1000) capacityDesignMah else 5000
                    val deltaPercent = levelPercent - lastLevelPercent
                    val calculatedMa = (deltaPercent.toDouble() / 100.0) * capacity * (3600.0 / elapsedSec)
                    if (calculatedMa in 200.0..12000.0) {
                        empiricalCurrentMa = calculatedMa
                    }
                }
            }
            lastLevelPercent = levelPercent
            lastLevelCheckTime = now

            val targetWatt = if (empiricalCurrentMa > 100.0) {
                val empiricalWatt = (empiricalCurrentMa / 1000.0) * (if (voltageVolt > 0.0) voltageVolt else 4.0)
                empiricalWatt
            } else {
                val maxRatedWatt = settings.chargerWattageRating.toDouble()
                when (plugType) {
                    "USB" -> 2.5
                    "Wireless" -> 7.5
                    else -> {
                        when {
                            levelPercent < 60 -> maxRatedWatt
                            levelPercent < 75 -> maxRatedWatt * 0.78
                            levelPercent < 85 -> maxRatedWatt * 0.52
                            levelPercent < 93 -> maxRatedWatt * 0.30
                            levelPercent < 98 -> maxRatedWatt * 0.16
                            else -> 2.5
                        }
                    }
                }
            }

            val effectiveVolt = if (voltageVolt > 2.0) voltageVolt else 4.0
            val computedCurrentMa = (targetWatt / effectiveVolt) * 1000.0
            selectedRawCurrent = (computedCurrentMa * 1000.0).toLong()
            dataSource = "Estimasi Cerdas (OPPO/MediaTek HAL)"
            isEstimated = true
        }

        val (currentMa, detectedUnit) = if (isEstimated) {
            Pair(selectedRawCurrent / 1000.0, "Estimasi")
        } else {
            convertToMilliamperes(selectedRawCurrent, settings.unitMultiplier)
        }

        val finalCurrentMa = if (settings.invertCurrentSign && !isEstimated) {
            -currentMa
        } else {
            currentMa
        }

        val watt = (abs(finalCurrentMa) / 1000.0) * (if (voltageVolt > 0.0) voltageVolt else 4.0)

        val isZeroCurrentWarning = (selectedRawCurrent == 0L || !isValidCurrent(selectedRawCurrent))

        val explanation = when {
            isEstimated -> {
                "Chipset MediaTek mt6768 / ColorOS membatasi driver CURRENT_NOW ke pihak ketiga. Mode Estimasi Cerdas aktif menghitung watt secara real time."
            }
            isZeroCurrentWarning -> {
                "Sensor arus perangkat melaporkan 0 mA. Driver CURRENT_NOW dibatasi oleh kernel OEM perangkat ini."
            }
            else -> {
                "Berhasil membaca data dari $dataSource ($detectedUnit)."
            }
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
            currentEstimated = isEstimated,
            voltageEstimated = voltageResult.estimated,
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

    private fun readSysfsFile(path: String): String? {
        return try {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                file.readText().trim()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "Unable to read $path", e)
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
            Log.w(TAG, "Unable to read design capacity", e)
            -1
        }
    }

    private companion object {
        const val TAG = "BatteryReader"
        const val SYSFS_SCAN_INTERVAL_MS = 10_000L
    }
}
