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
import com.ronin2022.matelinuxlauncher.termux.InstallProgressParser
import com.ronin2022.matelinuxlauncher.termux.TermuxCommandClient
import com.ronin2022.matelinuxlauncher.termux.TermuxCommandResult
import com.ronin2022.matelinuxlauncher.termux.TermuxContract
import com.ronin2022.matelinuxlauncher.termux.TermuxProbeParser
import com.ronin2022.matelinuxlauncher.termux.TermuxResultBus
import com.ronin2022.matelinuxlauncher.termux.TermuxResultStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
enum class X11DrawingMode { STANDARD, LEGACY }

data class ManagedActionStatus(
    val phase: ManagedActionPhase = ManagedActionPhase.IDLE,
    val message: String = "Hazır.",
    val executionId: Int? = null,
    val startedAtEpochMs: Long = 0L,
    val timeoutMs: Long = 0L,
    val isInstallation: Boolean = false,
    val progressStage: String? = null,
    val progressPercent: Int? = null,
    val progressDetail: String? = null,
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
    val x11DrawingMode: X11DrawingMode = X11DrawingMode.STANDARD,
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
            val recoveredManagedStatus = recoverStaleManagedAction(
                previous.managedActionStatus,
                System.currentTimeMillis(),
            )
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
                managedActionStatus = recoveredManagedStatus,
                x11DrawingMode = previous.x11DrawingMode,
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
                    scheduleProbeTimeout(executionId)
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

    fun setX11DrawingMode(mode: X11DrawingMode) {
        if (mutableUiState.value.managedActionStatus.phase == ManagedActionPhase.RUNNING) return
        mutableUiState.update { it.copy(x11DrawingMode = mode) }
    }

    fun startStableX11(): Boolean {
        val mode = mutableUiState.value.x11DrawingMode
        return executeManaged(
            startMessage = if (mode == X11DrawingMode.LEGACY) {
                "Huawei uyumluluk modunda Legacy X11 oturumu hazırlanıyor…"
            } else {
                "Huawei uyumluluk modunda Standart X11 oturumu hazırlanıyor…"
            },
            timeoutMs = X11_START_TIMEOUT_MS,
            requireFloat = true,
            action = { termuxClient.startStableX11(mode == X11DrawingMode.LEGACY) },
        )
    }

    fun stopManagedSession(): Boolean = executeManaged(
        startMessage = "MateLinuxLauncher oturumu kapatılıyor…",
        timeoutMs = STOP_TIMEOUT_MS,
        allowDuringRunning = true,
        action = termuxClient::stopManagedSession,
    )

    fun forceResetEnvironment(): Boolean = executeManaged(
        startMessage = "Test ortamı zorla sıfırlanıyor…",
        timeoutMs = FORCE_RESET_TIMEOUT_MS,
        allowDuringRunning = true,
        action = termuxClient::forceResetEnvironment,
    )

    fun installXfceTerminal(): Boolean = executeManaged(
        startMessage = "XFCE Terminal kurulumu hazırlanıyor…",
        timeoutMs = PACKAGE_INSTALL_TIMEOUT_MS,
        trackInstallProgress = true,
        action = termuxClient::installXfceTerminal,
    )

    fun startXfceTerminal(): Boolean = executeManaged(
        startMessage = "XFCE Terminal Float oturumunda başlatılıyor…",
        timeoutMs = APP_START_TIMEOUT_MS,
        requireFloat = true,
        action = termuxClient::startXfceTerminal,
    )

    fun installGeany(): Boolean = executeManaged(
        startMessage = "Geany kurulumu hazırlanıyor…",
        timeoutMs = PACKAGE_INSTALL_TIMEOUT_MS,
        trackInstallProgress = true,
        action = termuxClient::installGeany,
    )

    fun startGeany(): Boolean = executeManaged(
        startMessage = "Geany Float oturumunda başlatılıyor…",
        timeoutMs = APP_START_TIMEOUT_MS,
        requireFloat = true,
        action = termuxClient::startGeany,
    )

    fun installGimp(): Boolean = executeManaged(
        startMessage = "GIMP kurulumu hazırlanıyor…",
        timeoutMs = PACKAGE_INSTALL_TIMEOUT_MS,
        trackInstallProgress = true,
        action = termuxClient::installGimp,
    )

    fun startGimp(): Boolean = executeManaged(
        startMessage = "GIMP Float oturumunda başlatılıyor…",
        timeoutMs = APP_START_TIMEOUT_MS,
        requireFloat = true,
        action = termuxClient::startGimp,
    )

    fun installLibreOffice(): Boolean = executeManaged(
        startMessage = "LibreOffice kurulumu hazırlanıyor…",
        timeoutMs = PACKAGE_INSTALL_TIMEOUT_MS,
        trackInstallProgress = true,
        action = termuxClient::installLibreOffice,
    )

    fun startWriter(): Boolean = executeManaged(
        startMessage = "LibreOffice Writer Float oturumunda başlatılıyor…",
        timeoutMs = APP_START_TIMEOUT_MS,
        requireFloat = true,
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
        timeoutMs: Long,
        requireFloat: Boolean = false,
        allowDuringRunning: Boolean = false,
        trackInstallProgress: Boolean = false,
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
            requireFloat && !isInstalled(DependencyId.TERMUX_FLOAT) -> {
                setMessage("Huawei uyumluluk modu için Termux:Float kurulu olmalı.")
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
            state.managedActionStatus.phase == ManagedActionPhase.RUNNING && !allowDuringRunning -> {
                setMessage("Başka bir işlem sürüyor. Yanıt gelmezse uygulama kısa süre içinde otomatik olarak kilidi açacak.")
                return false
            }
        }

        return action().fold(
            onSuccess = { executionId ->
                val now = System.currentTimeMillis()
                mutableUiState.update {
                    it.copy(
                        managedActionStatus = ManagedActionStatus(
                            phase = ManagedActionPhase.RUNNING,
                            message = startMessage,
                            executionId = executionId,
                            startedAtEpochMs = now,
                            timeoutMs = timeoutMs,
                            isInstallation = trackInstallProgress,
                        ),
                        transientMessage = null,
                    )
                }
                scheduleManagedTimeout(executionId, timeoutMs)
                if (trackInstallProgress) scheduleInstallProgressPolling(executionId)
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

    private fun scheduleInstallProgressPolling(installExecutionId: Int) {
        viewModelScope.launch {
            while (true) {
                delay(INSTALL_PROGRESS_POLL_MS)
                val current = mutableUiState.value.managedActionStatus
                if (
                    current.phase != ManagedActionPhase.RUNNING ||
                    current.executionId != installExecutionId ||
                    !current.isInstallation
                ) break
                termuxClient.readInstallProgress()
            }
        }
    }

    private fun scheduleManagedTimeout(executionId: Int, timeoutMs: Long) {
        viewModelScope.launch {
            delay(timeoutMs)
            mutableUiState.update { state ->
                val current = state.managedActionStatus
                if (
                    current.phase == ManagedActionPhase.RUNNING &&
                    current.executionId == executionId
                ) {
                    state.copy(
                        managedActionStatus = current.copy(
                            phase = ManagedActionPhase.ERROR,
                            message = "Termux bu işleme zamanında yanıt vermedi. Düğmeler yeniden açıldı; gerekirse Test ortamını zorla sıfırla ile temizleyebilirsin.",
                            isInstallation = false,
                        ),
                    )
                } else {
                    state
                }
            }
        }
    }

    private fun scheduleProbeTimeout(executionId: Int) {
        viewModelScope.launch {
            delay(PROBE_TIMEOUT_MS)
            mutableUiState.update { state ->
                if (
                    state.probeStatus.phase == ProbePhase.RUNNING &&
                    state.probeStatus.executionId == executionId
                ) {
                    state.copy(
                        probeStatus = ProbeStatus(
                            phase = ProbePhase.ERROR,
                            message = "Termux teşhisi zaman aşımına uğradı; tekrar deneyebilirsin.",
                            executionId = executionId,
                        ),
                    )
                } else {
                    state
                }
            }
        }
    }

    private fun handleTermuxResult(result: TermuxCommandResult) {
        when (result.kind) {
            TermuxContract.RESULT_KIND_PROBE -> handleProbeResult(result)
            TermuxContract.RESULT_KIND_INSTALL_PROGRESS -> handleInstallProgress(result)
            else -> handleManagedResult(result)
        }
    }

    private fun handleInstallProgress(result: TermuxCommandResult) {
        if (!result.isSuccessful()) return
        val progress = InstallProgressParser.parse(result.stdout) ?: return
        mutableUiState.update { state ->
            val current = state.managedActionStatus
            if (current.phase != ManagedActionPhase.RUNNING || !current.isInstallation) {
                state
            } else {
                state.copy(
                    managedActionStatus = current.copy(
                        progressStage = progress.stage,
                        progressPercent = progress.percent,
                        progressDetail = progress.message,
                    ),
                )
            }
        }
    }

    private fun handleProbeResult(result: TermuxCommandResult) {
        if (mutableUiState.value.probeStatus.executionId != result.executionId) return

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
        if (
            current.phase != ManagedActionPhase.RUNNING ||
            current.executionId != result.executionId
        ) return

        if (!result.isSuccessful()) {
            mutableUiState.update {
                it.copy(
                    managedActionStatus = current.copy(
                        phase = ManagedActionPhase.ERROR,
                        message = managedFailureMessage(result),
                        isInstallation = false,
                    ),
                )
            }
            return
        }

        val message = when (result.kind) {
            TermuxContract.RESULT_KIND_START_X11 ->
                "X11, xfwm4 ve Float uygulama dağıtıcısı hazır."
            TermuxContract.RESULT_KIND_STOP_SESSION -> "Yönetilen Linux oturumu kapatıldı."
            TermuxContract.RESULT_KIND_FORCE_RESET -> "Test ortamı zorla sıfırlandı. Yeni oturum başlatabilirsin."
            TermuxContract.RESULT_KIND_INSTALL_TERMINAL -> "XFCE Terminal ve GUI çalışma bileşenleri kuruldu."
            TermuxContract.RESULT_KIND_START_TERMINAL -> "XFCE Terminal başlatıldı."
            TermuxContract.RESULT_KIND_INSTALL_GEANY -> "Geany kurulumu tamamlandı."
            TermuxContract.RESULT_KIND_START_GEANY -> "Geany başlatıldı."
            TermuxContract.RESULT_KIND_INSTALL_GIMP -> "GIMP kurulumu tamamlandı."
            TermuxContract.RESULT_KIND_START_GIMP -> "GIMP başlatıldı."
            TermuxContract.RESULT_KIND_INSTALL_WRITER -> "LibreOffice kurulumu tamamlandı."
            TermuxContract.RESULT_KIND_START_WRITER -> "LibreOffice Writer başlatıldı."
            else -> "İşlem tamamlandı."
        }

        val idleResult = result.kind in setOf(
            TermuxContract.RESULT_KIND_STOP_SESSION,
            TermuxContract.RESULT_KIND_FORCE_RESET,
        )
        mutableUiState.update {
            it.copy(
                managedActionStatus = ManagedActionStatus(
                    phase = if (idleResult) ManagedActionPhase.IDLE else ManagedActionPhase.SUCCESS,
                    message = message,
                    executionId = result.executionId,
                    progressStage = current.progressStage,
                    progressPercent = if (current.isInstallation) 100 else null,
                    progressDetail = current.progressDetail,
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
            "MISSING=gsettings-desktop-schemas" in combined ->
                "GTK şemaları eksik. İlgili uygulamada Kur düğmesine bir kez bas."
            "MISSING=xfce4-terminal" in combined ->
                "XFCE Terminal kurulu değil. Önce Kur düğmesine bas."
            "MISSING=geany" in combined -> "Geany kurulu değil. Önce Kur düğmesine bas."
            "MISSING=gimp" in combined -> "GIMP kurulu değil. Önce Kur düğmesine bas."
            "MISSING=libreoffice" in combined ->
                "LibreOffice kurulu değil. Önce Kur düğmesine bas."
            "ORPHAN_X11_DETECTED=" in combined ->
                "Launcher dışında başlatılmış bir X11 süreci bulundu. Test ortamını zorla sıfırla düğmesine bas, ardından yeni oturum başlat."
            "FLOAT_START_TIMEOUT=1" in combined ->
                "Termux:Float yeni oturumu zamanında açılmadı. Test ortamını zorla sıfırla ve yeniden dene."
            "FLOAT_X11_NOT_RUNNING=1" in combined ->
                "Float içindeki X11 süreci artık çalışmıyor. Önce Kararlı X11 oturumu başlat düğmesini kullan."
            "FLOAT_DISPATCHER_NOT_RUNNING=1" in combined ->
                "Float uygulama dağıtıcısı kapanmış. Kararlı X11 oturumunu yeniden başlat."
            "FLOAT_APP_TIMEOUT=" in combined ->
                "Linux uygulaması Float oturumundan zamanında yanıt vermedi. Oturumu temizleyip yeniden başlat."
            "APP_PROCESS_DIED=" in combined || "APP_START_FAILED=" in combined ->
                "Linux uygulaması başlatıldıktan hemen sonra kapandı. Uygulama logu yerel MateLinuxLauncher klasöründe tutuldu."
            "FLOAT_X11_START_FAILED=1" in combined || "X11_START_FAILED=1" in combined ->
                "X11 başlatılamadı. Test ortamını zorla sıfırla ve yeniden dene."
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

    companion object {
        const val APP_START_TIMEOUT_MS = 25_000L
        const val X11_START_TIMEOUT_MS = 35_000L
        const val STOP_TIMEOUT_MS = 12_000L
        const val FORCE_RESET_TIMEOUT_MS = 15_000L
        const val PACKAGE_INSTALL_TIMEOUT_MS = 30 * 60_000L
        const val PROBE_TIMEOUT_MS = 20_000L
        const val INSTALL_PROGRESS_POLL_MS = 1_200L

        internal fun recoverStaleManagedAction(
            status: ManagedActionStatus,
            nowEpochMs: Long,
        ): ManagedActionStatus {
            if (status.phase != ManagedActionPhase.RUNNING) return status
            if (status.startedAtEpochMs <= 0L || status.timeoutMs <= 0L) {
                return ManagedActionStatus(
                    phase = ManagedActionPhase.ERROR,
                    message = "Önceki işlem yarım kaldı. Düğmeler yeniden açıldı; gerekirse test ortamını sıfırlayabilirsin.",
                )
            }
            if (nowEpochMs - status.startedAtEpochMs < status.timeoutMs) return status
            return ManagedActionStatus(
                phase = ManagedActionPhase.ERROR,
                message = "Önceki işlem zaman aşımına uğradı. Düğmeler yeniden açıldı.",
            )
        }
    }
}
