package com.ronin2022.matelinuxlauncher.termux

import com.ronin2022.matelinuxlauncher.domain.LinuxRuntimeProbe

object TermuxProbeParser {

    fun parse(stdout: String): Result<LinuxRuntimeProbe> = runCatching {
        val values = stdout.lineSequence().mapNotNull(::parseLine).toMap()
        val version = values["PROBE_VERSION"]?.toIntOrNull()
            ?: error("PROBE_VERSION bulunamadı.")
        check(version == 1) { "Desteklenmeyen prob sürümü: $version" }

        LinuxRuntimeProbe(
            probeVersion = version,
            architecture = values["ARCH"]?.takeIf { it.isNotBlank() },
            wordSize = values["WORD_SIZE"]?.toIntOrNull(),
            osSummary = values["OS_SUMMARY"]?.takeIf { it.isNotBlank() },
            termuxX11Available = values.boolean("TERMUX_X11"),
            virglAvailable = values.boolean("VIRGL"),
            angleAndroidInstalled = values.boolean("ANGLE_ANDROID"),
            glxInfoAvailable = values.boolean("GLXINFO"),
            prootDistroAvailable = values.boolean("PROOT_DISTRO"),
            box64Available = values.boolean("BOX64"),
            allowExternalApps = values.boolean("ALLOW_EXTERNAL_APPS"),
        )
    }

    private fun parseLine(line: String): Pair<String, String>? {
        val separator = line.indexOf('=')
        if (separator <= 0) return null
        val key = line.substring(0, separator).trim()
        if (!key.matches(Regex("[A-Z0-9_]+"))) return null
        return key to line.substring(separator + 1).trim()
    }

    private fun Map<String, String>.boolean(key: String): Boolean =
        this[key] == "1" || this[key].equals("true", ignoreCase = true)
}
