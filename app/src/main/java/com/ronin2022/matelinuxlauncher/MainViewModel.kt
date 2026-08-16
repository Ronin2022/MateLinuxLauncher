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

enum class ManagedActionPhase { IDLE, RUNNING, SUCCESS, ERROR }

data class ManagedActionStatus(
    val phase: ManagedActionPhase = ManagedActionPhase.IDLE,
    val message: String = "Hazır.",
    val executionId: Int? = null,
)

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
    val managedActionStatus: ManagedActionStatus = ManagedActionStatus(),
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

            val previous = mutableUiState.value
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
                managedActionStatus = previous.managedActionStatus,
                transientMessage = previous.transientMessage,
            )
        }
    }

    fun runSafeTermuxProbe() {
        val state = mutableUiState.value
        when {
            !isInstalled(DependencyId.TERMUX) -> setMessage("Termux kurulu değil.")
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

    fun startStableX11(): Boolean = executeManaged(
        startMessage = "Huawei uyumluluk modunda X11 oturumu hazırlanıyor…",
        action = termuxClient::startStableX11,
    )

    fun stopManagedSession(): Boolean = executeManaged(
        startMessage = "MateLinuxLauncher oturumu kapatılıyor…",
        action = termuxClient::stopManagedSession,
    )

    fun installXfceTerminal(): Boolean = executeManaged(
        startMessage = "XFCE Terminal kuruluyor…",
        action = termuxClient::installXfceTerminal,
    )

    fun startXfceTerminal(): Boolean = executeManaged(
        startMessage = "XFCE Terminal başlatılıyor…",
        action = termuxClient::startXfceTerminal,
    )

    fun installGeany(): Boolean = executeManaged(
        startMessage = "Geany kuruluyor…",
        action = termuxClient::installGeany,
    )

    fun startGeany(): Boolean = executeManaged(
        startMessage = "Geany başlatılıyor…",
        action = termuxClient::startGeany,
    )

    fun installGimp(): Boolean = executeManaged(
        startMessage = "GIMP kuruluyor…",
        action = termuxClient::installGimp,
    )

    fun startGimp(): Boolean = executeManaged(
        startMessage = "GIMP başlatılıyor…",
        action = termuxClient::startGimp,
    )

    fun installLibreOffice(): Boolean = executeManaged(
        startMessage = "LibreOffice kuruluyor; bu paket büyük olduğu için biraz sürebilir…",
        action = termuxClient::installLibreOffice,
    )

    fun startWriter(): Boolean = executeManaged(
        startMessage = "LibreOffice Writer başlatılıyor…",
        action = termuxClient::startWriter,
    )

    fun requestShizukuPermission() {
        shizukuController.requestPermission()
            .onFailure { setMessage(it.message ?: "Shizuku izni istenemedi.") }
    }

    fun dismissMessage() {
        mutableUiState.update { it.copy(transientMessage = null) }
    }

    private fun executeManaged(
        startMessage: String,
        action: () -> Result<Int>,
    ): Boolean {
        val state = mutableUiState.value
        when {
            !isInstalled(DependencyId.TERMUX) -> {
                setMessage("Termux kurulu değil.")
                return false
            }
            !isInstalled(DependencyId.TERMUX_X11) -> {
                setMessage("Termux:X11 Android uygulaması kurulu değil.")
                return false
            }
            !state.termuxPermissionGranted -> {
                setMessage("Önce RUN_COMMAND iznini ver.")
                return false
            }
            state.runtime?.allowExternalApps == false -> {
                setMessage("Termux'ta allow-external-apps=true ayarı gerekli.")
                return false
            }
        }

        return action().fold(
            onSuccess = { executionId ->
                mutableUiState.update {
                    it.copy(
                        managedActionStatus = ManagedActionStatus(
                            phase = ManagedActionPhase.RUNNING,
                            message = startMessage,
                            executionId = executionId,
                        ),
                        transientMessage = null,
                    )
                }
                true
            },
            onFailure = { error ->
                mutableUiState.update {
                    it.copy(
                        managedActionStatus = ManagedActionStatus(
                            phase = ManagedActionPhase.ERROR,
                            message = error.message ?: "İşlem başlatılamadı.",
                        ),
                    )
                }
                false
            },
        )
    }

    private fun handleTermuxResult(result: TermuxCommandResult) {
        if (result.kind == TermuxContract.RESULT_KIND_PROBE) {
            handleProbeResult(result)
        } else {
            handleManagedResult(result)
        }
    }

    private fun handleProbeResult(result: TermuxCommandResult) {
        if (!result.isSuccessful()) {
            val detail = result.failureDetail("Termux teşhisi başarısız oldu.")
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

    private fun handleManagedResult(result: TermuxCommandResult) {
        val current = mutableUiState.value.managedActionStatus
        if (current.executionId != null && current.executionId != result.executionId) return

        if (!result.isSuccessful()) {
            mutableUiState.update {
                it.copy(
                    managedActionStatus = ManagedActionStatus(
                        phase = ManagedActionPhase.ERROR,
                        message = managedFailureMessage(result),
                        executionId = result.executionId,
                    ),
                )
            }
            return
        }

        val message = when (result.kind) {
            TermuxContract.RESULT_KIND_START_X11 ->
                "X11 oturumu hazır. Huawei'de Termux:Float'ı açık tutmak kararlılığı artırabilir."
            TermuxContract.RESULT_KIND_STOP_SESSION -> "Yönetilen Linux oturumu kapatıldı."
            TermuxContract.RESULT_KIND_INSTALL_TERMINAL -> "XFCE Terminal kurulumu tamamlandı."
            TermuxContract.RESULT_KIND_START_TERMINAL -> "XFCE Terminal başlatıldı."
            TermuxContract.RESULT_KIND_INSTALL_GEANY -> "Geany kurulumu tamamlandı."
            TermuxContract.RESULT_KIND_START_GEANY -> "Geany başlatıldı."
            TermuxContract.RESULT_KIND_INSTALL_GIMP -> "GIMP kurulumu tamamlandı."
            TermuxContract.RESULT_KIND_START_GIMP -> "GIMP başlatıldı."
            TermuxContract.RESULT_KIND_INSTALL_WRITER -> "LibreOffice kurulumu tamamlandı."
            TermuxContract.RESULT_KIND_START_WRITER -> "LibreOffice Writer başlatıldı."
            else -> "İşlem tamamlandı."
        }

        mutableUiState.update {
            it.copy(
                managedActionStatus = ManagedActionStatus(
                    phase = if (result.kind == TermuxContract.RESULT_KIND_STOP_SESSION) {
                        ManagedActionPhase.IDLE
                    } else {
                        ManagedActionPhase.SUCCESS
                    },
                    message = message,
                    executionId = result.executionId,
                ),
            )
        }
    }

    private fun managedFailureMessage(result: TermuxCommandResult): String {
        val combined = listOf(result.stdout, result.stderr, result.errorMessage)
            .joinToString("\n")
        return when {
            "MISSING=termux-x11" in combined ->
                "Termux içindeki termux-x11 eş paketi eksik. Güvenli teşhisi yeniden çalıştır."
            "MISSING=xfce4-terminal" in combined ->
                "XFCE Terminal kurulu değil. Önce Kur düğmesine bas."
            "MISSING=geany" in combined -> "Geany kurulu değil. Önce Kur düğmesine bas."
            "MISSING=gimp" in combined -> "GIMP kurulu değil. Önce Kur düğmesine bas."
            "MISSING=libreoffice" in combined ->
                "LibreOffice kurulu değil. Önce Kur düğmesine bas."
            "X11_START_FAILED=1" in combined ->
                "X11 başlatılamadı. Oturumu kapatıp yeniden dene; tekrarlarsa tanı toplayacağız."
            else -> result.failureDetail("İşlem başarısız oldu.")
        }
    }

    private fun TermuxCommandResult.isSuccessful(): Boolean =
        exitCode == 0 && errorCode in setOf(Int.MIN_VALUE, -1)

    private fun TermuxCommandResult.failureDetail(fallback: String): String =
        listOf(errorMessage, stderr, stdout)
            .firstOrNull { it.isNotBlank() }
            ?.takeLast(700)
            ?: "$fallback (çıkış $exitCode)"

    private fun isInstalled(id: DependencyId): Boolean =
        mutableUiState.value.dependencies.firstOrNull { it.id == id }?.installed == true

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
