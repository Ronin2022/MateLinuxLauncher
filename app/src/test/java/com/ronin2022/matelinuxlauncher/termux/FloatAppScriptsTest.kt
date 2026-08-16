package com.ronin2022.matelinuxlauncher.termux

import org.junit.Assert.assertTrue
import org.junit.Test

class FloatAppScriptsTest {

    @Test
    fun xfceTerminalRequiresDesktopSchemasBeforeLaunch() {
        val script = FloatAppScripts.START_TERMINAL_SCRIPT

        assertTrue(script.contains("gsettings-desktop-schemas"))
        assertTrue(script.contains("MISSING=gsettings-desktop-schemas"))
        assertTrue(script.contains("xfce4-terminal"))
        assertTrue(script.contains("GDK_BACKEND=x11"))
    }
}
