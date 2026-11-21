package com.antigravity.wattmeter.util

object VoltageNormalizer {
    data class Result(val volt: Double, val estimated: Boolean)

    fun normalize(raw: Long): Double {
        val volt = when {
            raw <= 0L -> 0.0
            raw >= 100_000L -> raw / 1_000_000.0
            raw >= 100L -> raw / 1000.0
            else -> raw.toDouble()
        }
        return if (volt in 2.5..6.0) volt else 0.0
    }

    fun estimateFromLevel(levelPercent: Int): Double {
        return 3.5 + 0.008 * levelPercent.coerceIn(0, 100)
    }

    fun resolve(rawIntent: Long, levelPercent: Int, sysfsRaw: Sequence<Long>): Result {
        val fromIntent = normalize(rawIntent)
        if (fromIntent > 0.0 && rawIntent >= 100L) return Result(fromIntent, false)

        for (raw in sysfsRaw) {
            val fromSysfs = normalize(raw)
            if (fromSysfs > 0.0) return Result(fromSysfs, false)
        }

        return Result(estimateFromLevel(levelPercent), true)
    }
}
