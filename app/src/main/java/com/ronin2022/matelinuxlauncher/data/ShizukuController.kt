package com.ronin2022.matelinuxlauncher.data

import android.content.pm.PackageManager
import com.ronin2022.matelinuxlauncher.domain.ShizukuState
import com.ronin2022.matelinuxlauncher.domain.ShizukuStatus
import rikka.shizuku.Shizuku

class ShizukuController {

    fun snapshot(installed: Boolean): ShizukuStatus {
        if (!installed) {
            return ShizukuStatus(
                state = ShizukuState.NOT_INSTALLED,
                detail = "Shizuku kurulu değil. Linux çalışması için zorunlu değildir.",
            )
        }

        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!binderAlive) {
            return ShizukuStatus(
                state = ShizukuState.SERVICE_STOPPED,
                detail = "Shizuku kurulu, fakat servis çalışmıyor.",
            )
        }

        return runCatching {
            when {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> {
                    val uid = Shizuku.getUid()
                    ShizukuStatus(
                        state = if (uid == 0) ShizukuState.READY_ROOT else ShizukuState.READY_ADB,
                        uid = uid,
                        detail = if (uid == 0) {
                            "Shizuku root kimliğiyle hazır."
                        } else {
                            "Shizuku ADB/shell kimliğiyle hazır (UID $uid)."
                        },
                    )
                }
                Shizuku.shouldShowRequestPermissionRationale() -> ShizukuStatus(
                    state = ShizukuState.PERMISSION_DENIED,
                    detail = "Shizuku izni reddedilmiş. Uygulama ayarlarından yeniden verilebilir.",
                )
                else -> ShizukuStatus(
                    state = ShizukuState.PERMISSION_REQUIRED,
                    detail = "Shizuku servisi hazır; MateLinuxLauncher izni gerekli.",
                )
            }
        }.getOrElse {
            ShizukuStatus(
                state = ShizukuState.UNKNOWN,
                detail = "Shizuku durumu okunamadı: ${it.message.orEmpty()}",
            )
        }
    }

    fun requestPermission(): Result<Unit> = runCatching {
        check(Shizuku.pingBinder()) { "Shizuku servisi çalışmıyor." }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }

    companion object {
        const val REQUEST_CODE = 4701
    }
}
