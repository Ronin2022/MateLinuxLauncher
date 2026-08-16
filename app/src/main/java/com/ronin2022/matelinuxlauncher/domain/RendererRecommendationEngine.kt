package com.ronin2022.matelinuxlauncher.domain

object RendererRecommendationEngine {

    fun recommend(
        device: DeviceProfile,
        dependencies: List<AppDependency>,
        runtime: LinuxRuntimeProbe?,
    ): List<RendererRecommendation> {
        val termuxInstalled = dependencies.installed(DependencyId.TERMUX)
        val x11AppInstalled = dependencies.installed(DependencyId.TERMUX_X11)
        val hasVulkan = !device.vulkanVersion.isNullOrBlank()
        val hasModernGles = device.glEsVersion.substringBefore('.').toIntOrNull()?.let { it >= 3 } ?: false

        val software = when {
            !termuxInstalled || !x11AppInstalled -> RendererRecommendation(
                id = "software-x11",
                title = "X11 güvenli 2D",
                state = RendererCandidateState.BLOCKED,
                reason = "Termux ve Termux:X11 Android uygulamaları birlikte gerekli.",
                nextStep = "Eksik uygulamaları aynı güvenilir dağıtım kaynağından kur.",
                priority = 10,
            )
            runtime == null -> RendererRecommendation(
                id = "software-x11",
                title = "X11 güvenli 2D",
                state = RendererCandidateState.NEEDS_PROBE,
                reason = "Android katmanı hazır; Termux içindeki eşlik eden paket henüz doğrulanmadı.",
                nextStep = "Güvenli Termux teşhisini çalıştır.",
                priority = 10,
            )
            !runtime.termuxX11Available -> RendererRecommendation(
                id = "software-x11",
                title = "X11 güvenli 2D",
                state = RendererCandidateState.BLOCKED,
                reason = "Termux içinde termux-x11 komutu bulunamadı.",
                nextStep = "Termux x11 deposunu ve termux-x11-nightly paketini kur.",
                priority = 10,
            )
            else -> RendererRecommendation(
                id = "software-x11",
                title = "X11 güvenli 2D",
                state = RendererCandidateState.FALLBACK,
                reason = "Termux:X11 zinciri mevcut; donanım hızlandırması olmadan temel doğrulama yapılabilir.",
                nextStep = "İlk pencere testini dengeli çözünürlükte çalıştır.",
                priority = 10,
            )
        }

        val angleVulkan = when {
            !hasVulkan -> RendererRecommendation(
                id = "virgl-angle-vulkan",
                title = "VirGL + ANGLE Vulkan",
                state = RendererCandidateState.BLOCKED,
                reason = "Android paket özelliklerinde Vulkan sürümü görünmüyor.",
                nextStep = "ANGLE OpenGL veya yazılımsal X11 yolunu kullan.",
                priority = 20,
            )
            runtime == null -> RendererRecommendation(
                id = "virgl-angle-vulkan",
                title = "VirGL + ANGLE Vulkan",
                state = RendererCandidateState.NEEDS_PROBE,
                reason = "Cihaz Vulkan bildiriyor; Termux paketleri henüz ölçülmedi.",
                nextStep = "Güvenli Termux teşhisini çalıştır.",
                priority = 20,
            )
            runtime.virglAvailable && runtime.angleAndroidInstalled -> RendererRecommendation(
                id = "virgl-angle-vulkan",
                title = "VirGL + ANGLE Vulkan",
                state = RendererCandidateState.AVAILABLE_FOR_BENCHMARK,
                reason = "Vulkan, virgl_test_server_android ve angle-android birlikte bulundu.",
                nextStep = "Süreli ve geri alınabilir GPU benchmark'ı çalıştır.",
                priority = 20,
            )
            else -> RendererRecommendation(
                id = "virgl-angle-vulkan",
                title = "VirGL + ANGLE Vulkan",
                state = RendererCandidateState.BLOCKED,
                reason = missingRendererParts(runtime),
                nextStep = "Eksik paketleri kurmadan renderer başlatma.",
                priority = 20,
            )
        }

        val angleOpenGl = when {
            !hasModernGles -> RendererRecommendation(
                id = "virgl-angle-gl",
                title = "VirGL + ANGLE OpenGL ES",
                state = RendererCandidateState.BLOCKED,
                reason = "OpenGL ES 3 veya üzeri doğrulanamadı.",
                nextStep = "Yazılımsal X11 yolunu kullan.",
                priority = 30,
            )
            runtime == null -> RendererRecommendation(
                id = "virgl-angle-gl",
                title = "VirGL + ANGLE OpenGL ES",
                state = RendererCandidateState.NEEDS_PROBE,
                reason = "Android OpenGL ES ${device.glEsVersion} bildiriyor; Termux paketleri henüz ölçülmedi.",
                nextStep = "Güvenli Termux teşhisini çalıştır.",
                priority = 30,
            )
            runtime.virglAvailable && runtime.angleAndroidInstalled -> RendererRecommendation(
                id = "virgl-angle-gl",
                title = "VirGL + ANGLE OpenGL ES",
                state = RendererCandidateState.AVAILABLE_FOR_BENCHMARK,
                reason = "OpenGL ES ve gerekli VirGL/ANGLE paketleri bulundu.",
                nextStep = "Vulkan adayı başarısız olursa ikinci donanım benchmark'ı olarak dene.",
                priority = 30,
            )
            else -> RendererRecommendation(
                id = "virgl-angle-gl",
                title = "VirGL + ANGLE OpenGL ES",
                state = RendererCandidateState.BLOCKED,
                reason = missingRendererParts(runtime),
                nextStep = "Eksik paketleri kurmadan renderer başlatma.",
                priority = 30,
            )
        }

        return listOf(software, angleVulkan, angleOpenGl).sortedBy { it.priority }
    }

    private fun List<AppDependency>.installed(id: DependencyId): Boolean =
        firstOrNull { it.id == id }?.installed == true

    private fun missingRendererParts(runtime: LinuxRuntimeProbe): String {
        val missing = buildList {
            if (!runtime.virglAvailable) add("virgl_test_server_android")
            if (!runtime.angleAndroidInstalled) add("angle-android")
        }
        return if (missing.isEmpty()) "Renderer bileşenleri doğrulanamadı."
        else "Eksik bileşen: ${missing.joinToString()}."
    }
}
