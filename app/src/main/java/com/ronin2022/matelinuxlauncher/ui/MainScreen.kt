package com.ronin2022.matelinuxlauncher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ronin2022.matelinuxlauncher.BuildConfig
import com.ronin2022.matelinuxlauncher.LauncherUiState
import com.ronin2022.matelinuxlauncher.MainViewModel
import com.ronin2022.matelinuxlauncher.domain.AppDependency
import com.ronin2022.matelinuxlauncher.domain.DeviceProfile
import com.ronin2022.matelinuxlauncher.domain.DeviceTuning
import com.ronin2022.matelinuxlauncher.domain.LinuxRuntimeProbe
import com.ronin2022.matelinuxlauncher.domain.ProbePhase
import com.ronin2022.matelinuxlauncher.domain.RendererCandidateState
import com.ronin2022.matelinuxlauncher.domain.RendererRecommendation
import com.ronin2022.matelinuxlauncher.domain.ShizukuState
import java.util.Locale

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onRequestTermuxPermission: () -> Unit,
    onOpenTermux: () -> Unit,
    onOpenTermuxX11: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.transientMessage) {
        state.transientMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.refreshing) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }
            item { Header() }
            state.device?.let { item { DeviceCard(it) } }
            item {
                DependencyCard(
                    state = state,
                    onRequestTermuxPermission = onRequestTermuxPermission,
                    onOpenTermux = onOpenTermux,
                    onOpenTermuxX11 = onOpenTermuxX11,
                    onRunProbe = viewModel::runSafeTermuxProbe,
                )
            }
            item {
                ShizukuCard(
                    state = state,
                    onRequestPermission = viewModel::requestShizukuPermission,
                    onOpenAppSettings = onOpenAppSettings,
                )
            }
            state.runtime?.let { item { RuntimeCard(it) } }
            state.tuning?.let { item { TuningCard(it) } }
            item { RecommendationHeader() }
            items(state.recommendations, key = { it.id }) { RecommendationCard(it) }
            item { SecurityCard() }
        }
    }
}

@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "MateLinuxLauncher",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "MRDI-W09 için Linux çalışma ortamı ve renderer hazırlığı",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Sürüm ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DeviceCard(device: DeviceProfile) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            SectionTitle("Cihaz profili")
            KeyValue("Model", "${device.manufacturer} ${device.model}")
            KeyValue("Android", "${device.androidVersion} / API ${device.sdkInt}")
            KeyValue("Mimari", device.supportedAbis.joinToString())
            KeyValue(
                "Bellek",
                "${formatBytes(device.availableMemoryBytes)} kullanılabilir / " +
                    "${formatBytes(device.totalMemoryBytes)} toplam",
            )
            KeyValue(
                "Ekran",
                "${device.screenWidthPx}×${device.screenHeightPx} / ${device.densityDpi} dpi",
            )
            KeyValue("OpenGL ES", device.glEsVersion)
            KeyValue("Vulkan", device.vulkanVersion ?: "Android özelliği görünmüyor")
            KeyValue(
                "GPU",
                listOfNotNull(device.gpu.vendor, device.gpu.renderer)
                    .joinToString(" · ")
                    .ifBlank { "EGL probu başarısız" },
            )
            device.gpu.version?.let { KeyValue("GPU API", it) }
            device.gpu.probeError?.let {
                Text(
                    text = "EGL notu: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun DependencyCard(
    state: LauncherUiState,
    onRequestTermuxPermission: () -> Unit,
    onOpenTermux: () -> Unit,
    onOpenTermuxX11: () -> Unit,
    onRunProbe: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionTitle("Linux katmanı")
            state.dependencies.forEach { DependencyRow(it) }
            HorizontalDivider()
            KeyValue(
                "RUN_COMMAND izni",
                if (state.termuxPermissionGranted) "Verildi" else "Gerekli",
            )
            Text(
                text = when (state.probeStatus.phase) {
                    ProbePhase.SUCCESS -> "✓ ${state.probeStatus.message}"
                    ProbePhase.ERROR -> "Hata: ${state.probeStatus.message}"
                    else -> state.probeStatus.message
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.probeStatus.phase == ProbePhase.ERROR) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )

            if (!state.termuxPermissionGranted) {
                Button(
                    onClick = onRequestTermuxPermission,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Termux komut iznini iste") }
            }

            Button(
                onClick = onRunProbe,
                enabled = state.probeStatus.phase != ProbePhase.RUNNING,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Güvenli Termux teşhisini çalıştır") }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onOpenTermux,
                    modifier = Modifier.weight(1f),
                ) { Text("Termux'u aç") }
                OutlinedButton(
                    onClick = onOpenTermuxX11,
                    modifier = Modifier.weight(1f),
                ) { Text("X11'i aç") }
            }
        }
    }
}

@Composable
private fun DependencyRow(dependency: AppDependency) {
    val role = if (dependency.required) "gerekli" else "isteğe bağlı"
    KeyValue(
        dependency.displayName,
        if (dependency.installed) {
            "Kurulu${dependency.versionName?.let { " · $it" }.orEmpty()} ($role)"
        } else {
            "Kurulu değil ($role)"
        },
    )
}

@Composable
private fun ShizukuCard(
    state: LauncherUiState,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionTitle("Shizuku yardımcı katmanı")
            Text(state.shizuku?.detail ?: "Durum okunuyor…")
            Text(
                text = "Shizuku Linux uygulamalarını çalıştırmak için zorunlu değildir; " +
                    "ileride Android teşhisi ve süreç gözetimi için kullanılacaktır.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (state.shizuku?.state) {
                ShizukuState.PERMISSION_REQUIRED -> Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Shizuku izni ver") }
                ShizukuState.PERMISSION_DENIED -> OutlinedButton(
                    onClick = onOpenAppSettings,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Uygulama ayarını aç") }
                else -> Unit
            }
        }
    }
}

@Composable
private fun RuntimeCard(runtime: LinuxRuntimeProbe) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            SectionTitle("Termux çalışma ortamı")
            KeyValue("Mimari", runtime.architecture ?: "Bilinmiyor")
            KeyValue("Bit", runtime.wordSize?.toString() ?: "Bilinmiyor")
            KeyValue("Sistem", runtime.osSummary ?: "Bilinmiyor")
            KeyValue("termux-x11", yesNo(runtime.termuxX11Available))
            KeyValue("VirGL Android", yesNo(runtime.virglAvailable))
            KeyValue("ANGLE Android", yesNo(runtime.angleAndroidInstalled))
            KeyValue("glxinfo", yesNo(runtime.glxInfoAvailable))
            KeyValue("proot-distro", yesNo(runtime.prootDistroAvailable))
            KeyValue("Box64", yesNo(runtime.box64Available))
            KeyValue("allow-external-apps", yesNo(runtime.allowExternalApps))
        }
    }
}

@Composable
private fun TuningCard(tuning: DeviceTuning) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionTitle(
                if (tuning.knownDevice) "Cihaza özel çözünürlük profili"
                else "Türetilmiş çözünürlük profili",
            )
            Text(tuning.profileName, fontWeight = FontWeight.SemiBold)
            tuning.presets.forEach { preset ->
                val defaultMark = if (preset.id == tuning.defaultPresetId) " · varsayılan" else ""
                KeyValue(
                    "${preset.title}$defaultMark",
                    "${preset.widthPx}×${preset.heightPx} (%${preset.scalePercent})",
                )
                Text(
                    text = preset.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            tuning.notes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun RecommendationHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Renderer adayları",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Buradaki sonuç seçim değil, bir sonraki kontrollü benchmark sırasıdır.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecommendationCard(recommendation: RendererRecommendation) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = recommendation.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = when (recommendation.state) {
                    RendererCandidateState.AVAILABLE_FOR_BENCHMARK -> "Benchmark'a hazır aday"
                    RendererCandidateState.NEEDS_PROBE -> "Termux probu gerekli"
                    RendererCandidateState.BLOCKED -> "Şimdilik engelli"
                    RendererCandidateState.FALLBACK -> "Güvenli geri dönüş yolu"
                },
                style = MaterialTheme.typography.labelLarge,
            )
            Text(recommendation.reason)
            Text(
                text = "Sonraki adım: ${recommendation.nextStep}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SecurityCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SectionTitle("Güvenlik sınırı")
            Text(
                text = "Bu sürüm kullanıcı girdisini shell'e göndermez, paket kurmaz, " +
                    "dosya silmez ve renderer başlatmaz. Prob yalnızca sabit teknik anahtarlar döndürür.",
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = key,
            modifier = Modifier.weight(0.42f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.58f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun yesNo(value: Boolean): String = if (value) "Var" else "Yok"

private fun formatBytes(bytes: Long): String {
    val gib = bytes / (1024.0 * 1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f GB", gib)
}
