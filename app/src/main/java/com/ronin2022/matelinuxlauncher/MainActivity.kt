package com.ronin2022.matelinuxlauncher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.ronin2022.matelinuxlauncher.data.PackageInspector
import com.ronin2022.matelinuxlauncher.termux.TermuxContract
import com.ronin2022.matelinuxlauncher.ui.MainScreen
import com.ronin2022.matelinuxlauncher.ui.MateLinuxTheme

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
                    onOpenAppSettings = { openAppSettings(packageName) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
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
