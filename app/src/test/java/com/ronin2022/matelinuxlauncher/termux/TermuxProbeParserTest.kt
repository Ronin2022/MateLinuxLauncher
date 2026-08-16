package com.ronin2022.matelinuxlauncher.termux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxProbeParserTest {

    @Test
    fun parsesKnownProbeKeysAndIgnoresNoise() {
        val output = """
            shell noise
            PROBE_VERSION=1
            ARCH=aarch64
            WORD_SIZE=64
            OS_SUMMARY=Linux 5.10
            TERMUX_X11=1
            VIRGL=1
            ANGLE_ANDROID=0
            GLXINFO=1
            PROOT_DISTRO=1
            BOX64=0
            ALLOW_EXTERNAL_APPS=true
            UNRECOGNIZED=value=with=equals
        """.trimIndent()

        val probe = TermuxProbeParser.parse(output).getOrThrow()

        assertEquals(1, probe.probeVersion)
        assertEquals("aarch64", probe.architecture)
        assertEquals(64, probe.wordSize)
        assertTrue(probe.termuxX11Available)
        assertTrue(probe.virglAvailable)
        assertFalse(probe.angleAndroidInstalled)
        assertTrue(probe.glxInfoAvailable)
        assertTrue(probe.prootDistroAvailable)
        assertFalse(probe.box64Available)
        assertTrue(probe.allowExternalApps)
    }

    @Test
    fun rejectsUnsupportedProbeVersion() {
        assertTrue(TermuxProbeParser.parse("PROBE_VERSION=9").isFailure)
    }
}
