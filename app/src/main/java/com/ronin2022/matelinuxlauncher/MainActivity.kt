package com.ronin2022.matelinuxlauncher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.ronin2022.matelinuxlauncher.data.PackageInspector
import com.ronin2022.matelinuxlauncher.termux.TermuxContract
import com.ronin2022.matelinuxlauncher.ui.MainScreen
import com.ronin2022.matelinuxlauncher.ui.MateLinuxTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<MainViewModel>()

    private val termuxPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MateLinuxTheme {
                MainScreen(
                    viewModel = viewModel,
                    onRequestTermuxPermission = {
                        termuxPermissionLauncher.launch(TermuxContract.PERMISSION_RUN_COMMAND)
                    },
                    onOpenTermux = { openPackage(PackageInspector.TERMUX_PACKAGE) },
                    onOpenTermuxX11 = { openPackage(PackageInspector.TERMUX_X11_PACKAGE) },
                    onOpenTermuxFloat = { openPackage(PackageInspector.TERMUX_FLOAT_PACKAGE) },
                    onStartStableSession = ::startStableSession,
                    onLaunchXfceTerminal = {
                        launchLinuxApp(viewModel::startXfceTerminal, openDelayMs = 1_800L)
                    },
                    onLaunchGeany = {
                        launchLinuxApp(viewModel::startGeany)
                    },
                    onLaunchGimp = {
                        launchLinuxApp(viewModel::startGimp)
                    },
                    onLaunchWriter = {
                        launchLinuxApp(viewModel::startWriter, openDelayMs = 1_800L)
                    },
                    onOpenAppSettings = { openAppSettings(packageName) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun startStableSession() {
        if (!viewModel.startStableX11()) return

        // RUN_COMMAND first installs the Float-only login hook and, if necessary, stops an old
        // Float service. Give that preparation time to finish, then open a brand-new Float login
        // shell. The hook starts X11 as a child of that visible Float session, matching the live
        // MRDI-W09 UAT sequence that remained stable.
        lifecycleScope.launch {
            delay(1_800L)
            openPackageIfInstalled(PackageInspector.TERMUX_FLOAT_PACKAGE)
            delay(5_000L)
            openPackage(PackageInspector.TERMUX_X11_PACKAGE)
        }
    }

    private fun launchLinuxApp(
        action: () -> Boolean,
        openDelayMs: Long = 1_100L,
    ) {
        if (!action()) return

        // Application launch commands are still submitted while MateLinuxLauncher is foreground.
        // Keeping Float visible preserves the shared Termux UID scheduling advantage on Huawei.
        openPackageIfInstalled(PackageInspector.TERMUX_FLOAT_PACKAGE)
        lifecycleScope.launch {
            delay(openDelayMs)
            openPackage(PackageInspector.TERMUX_X11_PACKAGE)
        }
    }

    private fun openPackageIfInstalled(targetPackage: String) {
        packageManager.getLaunchIntentForPackage(targetPackage)?.let(::startActivity)
    }

    private fun openPackage(targetPackage: String) {
        packageManager.getLaunchIntentForPackage(targetPackage)?.let(::startActivity)
            ?: openAppSettings(targetPackage)
    }

    private fun openAppSettings(targetPackage: String) {
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$targetPackage"),
                ),
            )
        }
    }
}
