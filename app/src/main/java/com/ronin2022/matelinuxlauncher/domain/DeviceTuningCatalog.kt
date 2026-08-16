package com.ronin2022.matelinuxlauncher.domain

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object DeviceTuningCatalog {

    fun forDevice(model: String, widthPx: Int, heightPx: Int): DeviceTuning {
        val landscapeWidth = max(widthPx, heightPx)
        val landscapeHeight = min(widthPx, heightPx)
        val isMrdiW09 = model.equals("MRDI-W09", ignoreCase = true)
        val matchesNativePanel = landscapeWidth == 2800 && landscapeHeight == 1840

        if (isMrdiW09 || matchesNativePanel) {
            return DeviceTuning(
                knownDevice = true,
                profileName = "Huawei MRDI-W09 / 2800×1840",
                defaultPresetId = "balanced",
                presets = listOf(
                    ResolutionPreset(
                        id = "economy",
                        title = "Ekonomik",
                        widthPx = 1680,
                        heightPx = 1104,
                        scalePercent = 60,
                        description = "Yazılımsal çizim ve ağır uygulamalar için düşük piksel yükü.",
                    ),
                    ResolutionPreset(
                        id = "balanced",
                        title = "Dengeli",
                        widthPx = 2100,
                        heightPx = 1380,
                        scalePercent = 75,
                        description = "MRDI-W09 için başlangıçta önerilen kalite/akıcılık dengesi.",
                    ),
                    ResolutionPreset(
                        id = "quality",
                        title = "Kalite",
                        widthPx = 2240,
                        heightPx = 1472,
                        scalePercent = 80,
                        description = "Hafif 2D uygulamalarda daha keskin görüntü.",
                    ),
                    ResolutionPreset(
                        id = "native",
                        title = "Doğal",
                        widthPx = 2800,
                        heightPx = 1840,
                        scalePercent = 100,
                        description = "Yalnızca hafif ve doğrulanmış uygulamalar için tam panel çözünürlüğü.",
                    ),
                ),
                notes = listOf(
                    "Varsayılan profil Android'in genel ekran çözünürlüğünü değiştirmez; yalnızca Linux oturumunu hedefler.",
                    "Maleoon GPU için doğrudan Mesa sürücüsü varsayılmaz; ANGLE yolları ölçülmeden seçilmez.",
                    "Tam masaüstü yerine tek uygulama + hafif pencere yöneticisi hedeflenir.",
                ),
            )
        }

        val safeWidth = landscapeWidth.coerceAtLeast(1280)
        val safeHeight = landscapeHeight.coerceAtLeast(720)
        val scales = listOf(60, 75, 85, 100)
        val ids = listOf("economy", "balanced", "quality", "native")
        val titles = listOf("Ekonomik", "Dengeli", "Kalite", "Doğal")

        return DeviceTuning(
            knownDevice = false,
            profileName = "$model / ${landscapeWidth}×$landscapeHeight",
            defaultPresetId = "balanced",
            presets = scales.mapIndexed { index, scale ->
                ResolutionPreset(
                    id = ids[index],
                    title = titles[index],
                    widthPx = scaledEven(safeWidth, scale),
                    heightPx = scaledEven(safeHeight, scale),
                    scalePercent = scale,
                    description = when (scale) {
                        60 -> "Ağır veya yazılımsal çizim kullanan uygulamalar için."
                        75 -> "Genel kullanım için başlangıç profili."
                        85 -> "Hafif uygulamalar için daha yüksek kalite."
                        else -> "Cihazın mevcut tam çözünürlüğü."
                    },
                )
            },
            notes = listOf(
                "Bu cihaz için doğrulanmış özel profil yok; değerler panel çözünürlüğünden türetildi.",
                "Renderer seçimi ancak güvenli prob ve benchmark sonrasında kalıcılaştırılmalıdır.",
            ),
        )
    }

    private fun scaledEven(value: Int, scalePercent: Int): Int {
        val scaled = (value * scalePercent / 100.0).roundToInt()
        return if (scaled % 2 == 0) scaled else scaled - 1
    }
}
