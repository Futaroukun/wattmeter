package com.antigravity.wattmeter.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoltageNormalizerTest {

    @Test
    fun millivoltIsConvertedToVolt() {
        assertEquals(4.2, VoltageNormalizer.normalize(4200L), 0.0001)
    }

    @Test
    fun microvoltIsConvertedToVolt() {
        assertEquals(4.2, VoltageNormalizer.normalize(4_200_000L), 0.0001)
    }

    @Test
    fun outOfRangeValueIsRejected() {
        assertEquals(0.0, VoltageNormalizer.normalize(0L), 0.0)
        assertEquals(0.0, VoltageNormalizer.normalize(-5L), 0.0)
        assertEquals(0.0, VoltageNormalizer.normalize(1500L), 0.0)
        assertEquals(0.0, VoltageNormalizer.normalize(900_000_000L), 0.0)
    }

    @Test
    fun validIntentValueIsUsedAsIs() {
        val result = VoltageNormalizer.resolve(4150L, 85, emptySequence())
        assertEquals(4.15, result.volt, 0.0001)
        assertFalse(result.estimated)
    }

    @Test
    fun coarseIntentValueFallsBackToSysfs() {
        val result = VoltageNormalizer.resolve(4L, 85, sequenceOf(4_180_000L))
        assertEquals(4.18, result.volt, 0.0001)
        assertFalse(result.estimated)
    }

    @Test
    fun missingSourcesFallBackToLevelEstimate() {
        val result = VoltageNormalizer.resolve(4L, 85, emptySequence())
        assertEquals(VoltageNormalizer.estimateFromLevel(85), result.volt, 0.0001)
        assertTrue(result.estimated)
    }

    @Test
    fun levelEstimateStaysInBatteryRange() {
        assertEquals(3.5, VoltageNormalizer.estimateFromLevel(-10), 0.0001)
        assertEquals(4.3, VoltageNormalizer.estimateFromLevel(150), 0.0001)
    }
}
