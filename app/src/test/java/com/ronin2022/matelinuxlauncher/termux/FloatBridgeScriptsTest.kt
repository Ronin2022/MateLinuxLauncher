package com.ronin2022.matelinuxlauncher.termux

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatBridgeScriptsTest {

    @Test
    fun stableX11ScriptUsesFloatOnlyLoginHookAndDispatcher() {
        val script = FloatBridgeScripts.START_X11_SCRIPT

        assertTrue(script.contains("MateLinuxLauncher Float bridge"))
        assertTrue(script.contains("TERMUX_APP__FILES_DIR"))
        assertTrue(script.contains("com.termux.window"))
        assertTrue(script.contains("float-pending.sh"))
        assertTrue(script.contains("termux-x11 :1 -dpi 240"))
        assertTrue(script.contains("float-dispatcher.pid"))
        assertTrue(script.contains("FLOAT_DISPATCHER_READY=1"))
        assertTrue(script.contains("X11_STARTED_VIA_FLOAT=1"))
        assertFalse(script.contains("pkill termux-x11"))
    }

    @Test
    fun stopScriptDoesNotSynchronouslyWaitForX11Receiver() {
        val script = FloatBridgeScripts.STOP_SESSION_SCRIPT

        assertTrue(script.contains("am broadcast -a com.termux.x11.ACTION_STOP"))
        assertTrue(script.contains("&"))
        assertTrue(script.contains("SESSION_STOPPED=1"))
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
