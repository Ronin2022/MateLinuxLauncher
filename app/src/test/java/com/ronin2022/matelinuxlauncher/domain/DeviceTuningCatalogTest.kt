package com.ronin2022.matelinuxlauncher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTuningCatalogTest {

    @Test
    fun selectsMrdiW09ProfileInPortraitOrientation() {
        val tuning = DeviceTuningCatalog.forDevice(
            model = "MRDI-W09",
            widthPx = 1840,
            heightPx = 2800,
        )

        assertTrue(tuning.knownDevice)
        assertEquals("balanced", tuning.defaultPresetId)
        assertEquals(2100, tuning.presets.first { it.id == "balanced" }.widthPx)
        assertEquals(1380, tuning.presets.first { it.id == "balanced" }.heightPx)
    }

    @Test
    fun generatedDimensionsAreEven() {
        val tuning = DeviceTuningCatalog.forDevice(
            model = "Unknown",
            widthPx = 1235,
            heightPx = 1999,
        )

        assertTrue(tuning.presets.all { it.widthPx % 2 == 0 && it.heightPx % 2 == 0 })
    }
}
