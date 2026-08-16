package com.ronin2022.matelinuxlauncher.domain

data class GpuProfile(
    val vendor: String?,
    val renderer: String?,
    val version: String?,
    val probeError: String? = null,
)

data class DeviceProfile(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdkInt: Int,
    val supportedAbis: List<String>,
    val totalMemoryBytes: Long,
    val availableMemoryBytes: Long,
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val densityDpi: Int,
    val glEsVersion: String,
    val vulkanVersion: String?,
    val gpu: GpuProfile,
)

enum class DependencyId { TERMUX, TERMUX_X11, TERMUX_FLOAT, SHIZUKU }

data class AppDependency(
    val id: DependencyId,
    val displayName: String,
    val packageName: String,
    val required: Boolean,
    val installed: Boolean,
    val versionName: String? = null,
)

enum class ShizukuState {
    NOT_INSTALLED,
    SERVICE_STOPPED,
    PERMISSION_REQUIRED,
    PERMISSION_DENIED,
    READY_ADB,
    READY_ROOT,
    UNKNOWN,
}

data class ShizukuStatus(
    val state: ShizukuState,
    val uid: Int? = null,
    val detail: String,
)

data class LinuxRuntimeProbe(
    val probeVersion: Int,
    val architecture: String?,
    val wordSize: Int?,
    val osSummary: String?,
    val termuxX11Available: Boolean,
    val virglAvailable: Boolean,
    val angleAndroidInstalled: Boolean,
    val glxInfoAvailable: Boolean,
    val prootDistroAvailable: Boolean,
    val box64Available: Boolean,
    val allowExternalApps: Boolean,
)

enum class ProbePhase { IDLE, RUNNING, SUCCESS, ERROR }

data class ProbeStatus(
    val phase: ProbePhase = ProbePhase.IDLE,
    val message: String = "Termux çalışma ortamı henüz ölçülmedi.",
    val executionId: Int? = null,
    val receivedAtEpochMs: Long? = null,
)

data class ResolutionPreset(
    val id: String,
    val title: String,
    val widthPx: Int,
    val heightPx: Int,
    val scalePercent: Int,
    val description: String,
)

data class DeviceTuning(
    val knownDevice: Boolean,
    val profileName: String,
    val defaultPresetId: String,
    val presets: List<ResolutionPreset>,
    val notes: List<String>,
)

enum class RendererCandidateState {
    AVAILABLE_FOR_BENCHMARK,
    NEEDS_PROBE,
    BLOCKED,
    FALLBACK,
}

data class RendererRecommendation(
    val id: String,
    val title: String,
    val state: RendererCandidateState,
    val reason: String,
    val nextStep: String,
    val priority: Int,
)
