package com.ronin2022.matelinuxlauncher.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RendererRecommendationEngineTest {

    private val device = DeviceProfile(
        manufacturer = "HUAWEI",
        model = "MRDI-W09",
        androidVersion = "12",
        sdkInt = 31,
        supportedAbis = listOf("arm64-v8a"),
        totalMemoryBytes = 12L * 1024 * 1024 * 1024,
        availableMemoryBytes = 4L * 1024 * 1024 * 1024,
        screenWidthPx = 1840,
        screenHeightPx = 2800,
        densityDpi = 360,
        glEsVersion = "3.2",
        vulkanVersion = "1.1.0",
        gpu = GpuProfile("HUAWEI", "Maleoon 920", "OpenGL ES 3.2"),
    )

    private val dependencies = listOf(
        AppDependency(
            DependencyId.TERMUX,
            "Termux",
            "com.termux",
            required = true,
            installed = true,
        ),
        AppDependency(
            DependencyId.TERMUX_X11,
            "Termux:X11",
            "com.termux.x11",
            required = true,
            installed = true,
        ),
    )

    @Test
    fun promotesVulkanOnlyAfterPackagesAreVerified() {
        val runtime = LinuxRuntimeProbe(
            probeVersion = 1,
            architecture = "aarch64",
            wordSize = 64,
            osSummary = "Linux",
            termuxX11Available = true,
            virglAvailable = true,
            angleAndroidInstalled = true,
            glxInfoAvailable = true,
            prootDistroAvailable = true,
            box64Available = false,
            allowExternalApps = true,
        )

        val result = RendererRecommendationEngine.recommend(
            device = device,
            dependencies = dependencies,
            runtime = runtime,
        )

        assertEquals(
            RendererCandidateState.AVAILABLE_FOR_BENCHMARK,
            result.first { it.id == "virgl-angle-vulkan" }.state,
        )
    }

    @Test
    fun doesNotClaimHardwarePathBeforeProbe() {
        val result = RendererRecommendationEngine.recommend(
            device = device,
            dependencies = dependencies,
            runtime = null,
        )

        assertEquals(
            RendererCandidateState.NEEDS_PROBE,
            result.first { it.id == "virgl-angle-vulkan" }.state,
        )
    }
}
