package com.ronin2022.matelinuxlauncher

import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ronin2022.matelinuxlauncher.data.DeviceProfiler
import com.ronin2022.matelinuxlauncher.data.PackageInspector
import com.ronin2022.matelinuxlauncher.data.ShizukuController
import com.ronin2022.matelinuxlauncher.domain.AppDependency
import com.ronin2022.matelinuxlauncher.domain.DependencyId
import com.ronin2022.matelinuxlauncher.domain.DeviceProfile
import com.ronin2022.matelinuxlauncher.domain.DeviceTuning
import com.ronin2022.matelinuxlauncher.domain.DeviceTuningCatalog
import com.ronin2022.matelinuxlauncher.domain.LinuxRuntimeProbe
import com.ronin2022.matelinuxlauncher.domain.ProbePhase
import com.ronin2022.matelinuxlauncher.domain.ProbeStatus
import com.ronin2022.matelinuxlauncher.domain.RendererRecommendation
import com.ronin2022.matelinuxlauncher.domain.RendererRecommendationEngine
import com.ronin2022.matelinuxlauncher.domain.ShizukuStatus
import com.ronin2022.matelinuxlauncher.termux.TermuxCommandClient
import com.ronin2022.matelinuxlauncher.termux.TermuxCommandResult
import com.ronin2022.matelinuxlauncher.termux.TermuxContract
import com.ronin2022.matelinuxlauncher.termux.TermuxProbeParser
import com.ronin2022.matelinuxlauncher.termux.TermuxResultBus
import com.ronin2022.matelinuxlauncher.termux.TermuxResultStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

data class LauncherUiState(
    val refreshing: Boolean = true,
    val device: DeviceProfile? = null,
    val dependencies: List<AppDependency> = emptyList(),
    val termuxPermissionGranted: Boolean = false,
    val shizuku: ShizukuStatus? = null,
    val runtime: LinuxRuntimeProbe? = null,
    val probeStatus: ProbeStatus = ProbeStatus(),
    val tuning: DeviceTuning? = null,
    val recommendations: List<RendererRecommendation> = emptyList(),
    val transientMessage: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application.applicationContext
    private val deviceProfiler = DeviceProfiler(app)
    private val packageInspector = PackageInspector(app)
    private val shizukuController = ShizukuController()
    private val termuxClient = TermuxCommandClient(app)
    private val resultStore = TermuxResultStore(app)

    private val mutableUiState = MutableStateFlow(LauncherUiState())
    val uiState: StateFlow<LauncherUiState> = mutableUiState.asStateFlow()

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }
    private val shizukuBinderReceivedListener =
        Shizuku.OnBinderReceivedListener { refresh() }
    private val shizukuBinderDeadListener =
        Shizuku.OnBinderDeadListener { refresh() }

    init {
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        Shizuku.addBinderReceivedListener(shizukuBinderReceivedListener)
        Shizuku.addBinderDeadListener(shizukuBinderDeadListener)

        TermuxResultBus.results
            .onEach(::handleTermuxResult)
            .launchIn(viewModelScope)

        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableUiState.update { it.copy(refreshing = true) }

            val device = withContext(Dispatchers.Default) { deviceProfiler.read() }
            val dependencies = withContext(Dispatchers.IO) { packageInspector.inspectAll() }
            val termuxPermission = ContextCompat.checkSelfPermission(
                app,
                TermuxContract.PERMISSION_RUN_COMMAND,
            ) == PackageManager.PERMISSION_GRANTED
            val shizukuInstalled = dependencies
                .firstOrNull { it.id == DependencyId.SHIZUKU }
                ?.installed == true
            val shizukuStatus = shizukuController.snapshot(shizukuInstalled)
            val tuning = DeviceTuningCatalog.forDevice(
                model = device.model,
                widthPx = device.screenWidthPx,
                heightPx = device.screenHeightPx,
            )

            var runtime = mutableUiState.value.runtime
            var probeStatus = mutableUiState.value.probeStatus
            if (runtime == null) {
                resultStore.load()?.let { stored ->
                    if (stored.exitCode == 0) {
                        TermuxProbeParser.parse(stored.stdout).getOrNull()?.let { parsed ->
                            runtime = parsed
                            probeStatus = ProbeStatus(
                                phase = ProbePhase.SUCCESS,
                                message = "Son güvenli Termux teşhisi yüklendi.",
                                executionId = stored.executionId,
                                receivedAtEpochMs = stored.receivedAtEpochMs,
                            )
                        }
                    }
                }
            }

            mutableUiState.value = LauncherUiState(
                refreshing = false,
                device = device,
                dependencies = dependencies,
                termuxPermissionGranted = termuxPermission,
                shizuku = shizukuStatus,
                runtime = runtime,
                probeStatus = probeStatus,
                tuning = tuning,
                recommendations = RendererRecommendationEngine.recommend(
                    device = device,
                    dependencies = dependencies,
                    runtime = runtime,
                ),
                transientMessage = mutableUiState.value.transientMessage,
            )
        }
    }

    fun runSafeTermuxProbe() {
        val state = mutableUiState.value
        val termuxInstalled = state.dependencies
            .firstOrNull { it.id == DependencyId.TERMUX }
            ?.installed == true

        when {
            !termuxInstalled -> setMessage("Termux kurulu değil.")
            !state.termuxPermissionGranted -> setMessage(
                "Önce Termux ortamında komut çalıştırma iznini ver.",
            )
            else -> termuxClient.runSafeEnvironmentProbe()
                .onSuccess { executionId ->
                    mutableUiState.update {
                        it.copy(
                            probeStatus = ProbeStatus(
                                phase = ProbePhase.RUNNING,
                                message = "Sabit ve salt-okunur Termux teşhisi çalışıyor…",
                                executionId = executionId,
                            ),
                            transientMessage = null,
                        )
                    }
                }
                .onFailure { error ->
                    mutableUiState.update {
                        it.copy(
                            probeStatus = ProbeStatus(
                                phase = ProbePhase.ERROR,
                                message = error.message ?: "Termux teşhisi başlatılamadı.",
                            ),
                        )
                    }
                }
        }
    }

    fun requestShizukuPermission() {
        shizukuController.requestPermission()
            .onFailure { setMessage(it.message ?: "Shizuku izni istenemedi.") }
    }

    fun dismissMessage() {
        mutableUiState.update { it.copy(transientMessage = null) }
    }

    private fun handleTermuxResult(result: TermuxCommandResult) {
        if (result.exitCode != 0 || result.errorCode !in setOf(Int.MIN_VALUE, -1)) {
            val detail = listOf(result.errorMessage, result.stderr)
                .firstOrNull { it.isNotBlank() }
                ?: "Termux komutu başarısız oldu (çıkış ${result.exitCode})."
            mutableUiState.update {
                it.copy(
                    probeStatus = ProbeStatus(
                        phase = ProbePhase.ERROR,
                        message = detail,
                        executionId = result.executionId,
                        receivedAtEpochMs = result.receivedAtEpochMs,
                    ),
                )
            }
            return
        }

        TermuxProbeParser.parse(result.stdout)
            .onSuccess { runtime ->
                val current = mutableUiState.value
                val device = current.device ?: return@onSuccess
                mutableUiState.update {
                    it.copy(
                        runtime = runtime,
                        probeStatus = ProbeStatus(
                            phase = ProbePhase.SUCCESS,
                            message = "Termux çalışma ortamı başarıyla doğrulandı.",
                            executionId = result.executionId,
                            receivedAtEpochMs = result.receivedAtEpochMs,
                        ),
                        recommendations = RendererRecommendationEngine.recommend(
                            device = device,
                            dependencies = current.dependencies,
                            runtime = runtime,
                        ),
                    )
                }
            }
            .onFailure { error ->
                mutableUiState.update {
                    it.copy(
                        probeStatus = ProbeStatus(
                            phase = ProbePhase.ERROR,
                            message = "Prob çıktısı çözümlenemedi: ${error.message.orEmpty()}",
                            executionId = result.executionId,
                            receivedAtEpochMs = result.receivedAtEpochMs,
                        ),
                    )
                }
            }
    }

    private fun setMessage(message: String) {
        mutableUiState.update { it.copy(transientMessage = message) }
    }

    override fun onCleared() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener)
        Shizuku.removeBinderDeadListener(shizukuBinderDeadListener)
        super.onCleared()
    }
}
