package com.ronin2022.matelinuxlauncher.termux

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatBridgeScriptsTest {

    @Test
    fun stableX11ScriptUsesFloatOnlyLoginHook() {
        val script = FloatBridgeScripts.START_X11_SCRIPT

        assertTrue(script.contains("MateLinuxLauncher Float bridge"))
        assertTrue(script.contains("TERMUX_APP__FILES_DIR"))
        assertTrue(script.contains("com.termux.window"))
        assertTrue(script.contains("float-pending.sh"))
        assertTrue(script.contains("termux-x11 :1 -dpi 240"))
        assertTrue(script.contains("X11_STARTED_VIA_FLOAT=1"))
        assertFalse(script.contains("pkill termux-x11"))
    }
}
