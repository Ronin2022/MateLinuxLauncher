package com.ronin2022.matelinuxlauncher.termux

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatBridgeScriptsTest {

    @Test
    fun stableX11ScriptUsesFloatOnlyLoginHookDispatcherAndWindowManager() {
        val script = FloatBridgeScripts.START_X11_SCRIPT

        assertTrue(script.contains("MateLinuxLauncher Float bridge"))
        assertTrue(script.contains("TERMUX_APP__FILES_DIR"))
        assertTrue(script.contains("com.termux.window"))
        assertTrue(script.contains("float-pending.sh"))
        assertTrue(script.contains("termux-x11 :1 -dpi 240"))
        assertTrue(script.contains("float-dispatcher.pid"))
        assertTrue(script.contains("xfwm4 --replace"))
        assertTrue(script.contains("xfwm4.pid"))
        assertTrue(script.contains("X11_STARTED_VIA_FLOAT=1"))
        assertTrue(script.contains("ORPHAN_X11_DETECTED"))
        assertFalse(script.contains("pkill -x termux-x11"))
    }

    @Test
    fun legacyModeAddsOnlyLegacyDrawingFlag() {
        val standard = FloatBridgeScripts.START_X11_SCRIPT
        val legacy = FloatBridgeScripts.START_X11_LEGACY_SCRIPT

        assertFalse(standard.contains("-legacy-drawing"))
        assertTrue(legacy.contains("termux-x11 :1 -dpi 240 -legacy-drawing"))
        assertTrue(legacy.contains("DRAWING_MODE=legacy"))
    }

    @Test
    fun stopScriptDoesNotSynchronouslyWaitForX11Receiver() {
        val script = FloatBridgeScripts.STOP_SESSION_SCRIPT

        assertTrue(script.contains("am broadcast -a com.termux.x11.ACTION_STOP"))
        assertTrue(script.contains("&"))
        assertTrue(script.contains("SESSION_STOPPED=1"))
        assertTrue(script.contains("xfwm4.pid"))
    }

    @Test
    fun forceResetUsesExactKnownProcessNames() {
        val script = FloatBridgeScripts.FORCE_RESET_SCRIPT

        assertTrue(script.contains("pkill -x"))
        assertTrue(script.contains("termux-x11"))
        assertTrue(script.contains("gimp-3.0"))
        assertTrue(script.contains("FORCE_RESET_COMPLETE=1"))
    }

    @Test
    fun appRequestsRequireLiveFloatDispatcher() {
        val script = FloatAppScripts.START_TERMINAL_SCRIPT

        assertTrue(script.contains("FLOAT_X11_NOT_RUNNING=1"))
        assertTrue(script.contains("FLOAT_DISPATCHER_NOT_RUNNING=1"))
        assertTrue(script.contains("float-queue"))
        assertTrue(script.contains("xfce4-terminal"))
    }
}
