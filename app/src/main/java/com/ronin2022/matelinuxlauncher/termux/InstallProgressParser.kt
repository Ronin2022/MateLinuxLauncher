package com.ronin2022.matelinuxlauncher.termux

data class InstallProgress(
    val stage: String,
    val percent: Int?,
    val message: String,
)

object InstallProgressParser {
    fun parse(output: String): InstallProgress? {
        val values = output.lineSequence()
            .mapNotNull { line ->
                val split = line.indexOf('=')
                if (split <= 0) null else line.substring(0, split) to line.substring(split + 1)
            }
            .toMap()

        val stage = values["stage"]?.trim().orEmpty()
        if (stage.isBlank()) return null

        val percent = values["percent"]
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.toIntOrNull()
            ?.coerceIn(0, 100)

        return InstallProgress(
            stage = stage,
            percent = percent,
            message = values["message"]?.trim().orEmpty(),
        )
    }
}