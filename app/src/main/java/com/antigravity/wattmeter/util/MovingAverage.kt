package com.antigravity.wattmeter.util

import java.util.ArrayDeque

class MovingAverage(val windowSize: Int) {
    private val effectiveWindowSize = windowSize.coerceAtLeast(1)
    private val window = ArrayDeque<Double>(effectiveWindowSize)
    private var sum = 0.0

    @Synchronized
    fun add(value: Double): Double {
        if (effectiveWindowSize <= 1) return value
        if (window.size == effectiveWindowSize) {
            sum -= window.removeFirst()
        }
        window.addLast(value)
        sum += value
        return sum / window.size
    }

    @Synchronized
    fun getAverage(): Double {
        if (window.isEmpty()) return 0.0
        return sum / window.size
    }

    @Synchronized
    fun reset() {
        window.clear()
        sum = 0.0
    }
}
