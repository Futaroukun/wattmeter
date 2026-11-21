package com.antigravity.wattmeter.data.model

data class DiagnosticInfo(
    val currentNowRaw: Long = 0L,
    val currentAverageRaw: Long = 0L,
    val sysfsChecked: List<Pair<String, String>> = emptyList(),
    val detectedUnit: String = "",
    val signReversed: Boolean = false,
    val isZeroCurrentWarning: Boolean = false,
    val explanation: String = ""
)
