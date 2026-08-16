package com.ronin2022.matelinuxlauncher.data

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.ronin2022.matelinuxlauncher.domain.AppDependency
import com.ronin2022.matelinuxlauncher.domain.DependencyId

class PackageInspector(private val context: Context) {

    fun inspectAll(): List<AppDependency> = listOf(
        inspect(DependencyId.TERMUX, "Termux", TERMUX_PACKAGE, required = true),
        inspect(DependencyId.TERMUX_X11, "Termux:X11", TERMUX_X11_PACKAGE, required = true),
        inspect(
            DependencyId.TERMUX_FLOAT,
            "Termux:Float",
            TERMUX_FLOAT_PACKAGE,
            required = false,
        ),
        inspect(DependencyId.SHIZUKU, "Shizuku", SHIZUKU_PACKAGE, required = false),
    )

    private fun inspect(
        id: DependencyId,
        displayName: String,
        packageName: String,
        required: Boolean,
    ): AppDependency {
        val info = getPackageInfo(packageName)
        return AppDependency(
            id = id,
            displayName = displayName,
            packageName = packageName,
            required = required,
            installed = info != null,
            versionName = info?.versionName,
        )
    }

    @Suppress("DEPRECATION")
    private fun getPackageInfo(packageName: String): PackageInfo? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            context.packageManager.getPackageInfo(packageName, 0)
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    companion object {
        const val TERMUX_PACKAGE = "com.termux"
        const val TERMUX_X11_PACKAGE = "com.termux.x11"
        const val TERMUX_FLOAT_PACKAGE = "com.termux.window"
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    }
}
