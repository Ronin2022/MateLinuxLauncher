package com.ronin2022.matelinuxlauncher.termux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstallProgressParserTest {
    @Test
    fun parsesRealAptProgressSnapshot() {
        val progress = InstallProgressParser.parse(
            """
            stage=İndiriliyor
            percent=37
            message=GIMP paketleri alınıyor
            """.trimIndent(),
        )

        requireNotNull(progress)
        assertEquals("İndiriliyor", progress.stage)
        assertEquals(37, progress.percent)
        assertEquals("GIMP paketleri alınıyor", progress.message)
    }

    @Test
    fun acceptsIndeterminateStage() {
        val progress = InstallProgressParser.parse("stage=Depo güncelleniyor\npercent=\nmessage=x11-repo")
        requireNotNull(progress)
        assertNull(progress.percent)
    }
}