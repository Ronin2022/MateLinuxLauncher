package com.ronin2022.matelinuxlauncher.termux

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.concurrent.atomic.AtomicInteger

class TermuxCommandClient(private val context: Context) {

    fun runSafeEnvironmentProbe(): Result<Int> = runScript(
        script = SAFE_PROBE_SCRIPT,
        label = "MateLinuxLauncher güvenli ortam teşhisi",
        description = "Yalnızca mimariyi ve seçili Linux/X11 komutlarının varlığını okur.",
        resultKind = TermuxContract.RESULT_KIND_PROBE,
    )

    fun startStableX11(legacyDrawing: Boolean): Result<Int> = runScript(
        script = if (legacyDrawing) {
            FloatBridgeScripts.START_X11_LEGACY_SCRIPT
        } else {
            FloatBridgeScripts.START_X11_SCRIPT
        },
        label = if (legacyDrawing) {
            "MateLinuxLauncher X11 legacy oturumu"
        } else {
            "MateLinuxLauncher X11 oturumu"
        },
        description = "Termux:Float login oturumunda X11, pencere yöneticisi ve uygulama dağıtıcısını başlatır.",
        resultKind = TermuxContract.RESULT_KIND_START_X11,
    )

    fun stopManagedSession(): Result<Int> = runScript(
        script = FloatBridgeScripts.STOP_SESSION_SCRIPT,
        label = "MateLinuxLauncher oturumu kapat",
        description = "Yalnızca MateLinuxLauncher tarafından kaydedilmiş süreçleri durdurur ve wakelock'u bırakır.",
        resultKind = TermuxContract.RESULT_KIND_STOP_SESSION,
    )

    fun forceResetEnvironment(): Result<Int> = runScript(
        script = FloatBridgeScripts.FORCE_RESET_SCRIPT,
        label = "MateLinuxLauncher test ortamını zorla sıfırla",
        description = "Termux:X11 testlerinden kalmış bilinen sahipsiz GUI süreçlerini ve geçici oturum durumunu temizler.",
        resultKind = TermuxContract.RESULT_KIND_FORCE_RESET,
    )

    fun readInstallProgress(): Result<Int> = runScript(
        script = """
            file="${'$'}HOME/.matelinuxlauncher/install-progress.txt"
            if [ -f "${'$'}file" ]; then
              cat "${'$'}file"
            else
              printf 'stage=Başlatılıyor\npercent=\nmessage=Termux hazırlanıyor\n'
            fi
        """.trimIndent(),
        label = "Kurulum ilerlemesini oku",
        description = "Yalnızca MateLinuxLauncher'ın kendi kurulum ilerleme dosyasını okur.",
        resultKind = TermuxContract.RESULT_KIND_INSTALL_PROGRESS,
    )

    fun installXfceTerminal(): Result<Int> = installPackages(
        packageNames = GUI_RUNTIME_PACKAGES + "xfce4-terminal",
        displayName = "XFCE Terminal",
        logName = "install-xfce4-terminal.log",
        resultKind = TermuxContract.RESULT_KIND_INSTALL_TERMINAL,
        label = "XFCE Terminal kur",
    )

    fun startXfceTerminal(): Result<Int> = runScript(
        script = FloatAppScripts.START_TERMINAL_SCRIPT,
        label = "XFCE Terminal aç",
        description = "XFCE Terminal'i çalışan Termux:Float/X11 oturumunda açar.",
        resultKind = TermuxContract.RESULT_KIND_START_TERMINAL,
    )

    fun installGeany(): Result<Int> = installPackages(
        packageNames = GUI_RUNTIME_PACKAGES + "geany",
        displayName = "Geany",
        logName = "install-geany.log",
        resultKind = TermuxContract.RESULT_KIND_INSTALL_GEANY,
        label = "Geany kur",
    )

    fun startGeany(): Result<Int> = runScript(
        script = FloatAppScripts.START_GEANY_SCRIPT,
        label = "Geany aç",
        description = "Geany'yi çalışan Termux:Float/X11 oturumunda açar.",
        resultKind = TermuxContract.RESULT_KIND_START_GEANY,
    )

    fun installGimp(): Result<Int> = installPackages(
        packageNames = GUI_RUNTIME_PACKAGES + "gimp",
        displayName = "GIMP",
        logName = "install-gimp.log",
        resultKind = TermuxContract.RESULT_KIND_INSTALL_GIMP,
        label = "GIMP kur",
    )

    fun startGimp(): Result<Int> = runScript(
        script = FloatAppScripts.START_GIMP_SCRIPT,
        label = "GIMP aç",
        description = "GIMP'i çalışan Termux:Float/X11 oturumunda açar.",
        resultKind = TermuxContract.RESULT_KIND_START_GIMP,
    )

    fun installLibreOffice(): Result<Int> = installPackages(
        packageNames = GUI_RUNTIME_PACKAGES + "libreoffice",
        displayName = "LibreOffice",
        logName = "install-libreoffice.log",
        resultKind = TermuxContract.RESULT_KIND_INSTALL_WRITER,
        label = "LibreOffice kur",
    )

    fun startWriter(): Result<Int> = runScript(
        script = FloatAppScripts.START_WRITER_SCRIPT,
        label = "LibreOffice Writer aç",
        description = "LibreOffice Writer'ı çalışan Termux:Float/X11 oturumunda açar.",
        resultKind = TermuxContract.RESULT_KIND_START_WRITER,
    )

    private fun installPackages(
        packageNames: List<String>,
        displayName: String,
        logName: String,
        resultKind: String,
        label: String,
    ): Result<Int> = runScript(
        script = installPackageScript(packageNames.distinct(), displayName, logName),
        label = label,
        description = "${packageNames.distinct().joinToString()} paketlerini Termux X11 deposundan kurar ve gerçek APT aşamasını yerel ilerleme dosyasına yazar.",
        resultKind = resultKind,
    )

    private fun runScript(
        script: String,
        label: String,
        description: String,
        resultKind: String,
    ): Result<Int> = runCatching {
        val executionId = nextExecutionId()
        val resultIntent = Intent(context, TermuxResultReceiver::class.java).apply {
            action = TermuxContract.ACTION_COMMAND_RESULT
            putExtra(TermuxContract.EXTRA_EXECUTION_ID, executionId)
            putExtra(TermuxContract.EXTRA_RESULT_KIND, resultKind)
        }
        val pendingIntentFlags = PendingIntent.FLAG_ONE_SHOT or
            PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
        val resultPendingIntent = PendingIntent.getBroadcast(
            context,
            executionId,
            resultIntent,
            pendingIntentFlags,
        )

        val commandIntent = Intent().apply {
            component = ComponentName(
                TermuxContract.PACKAGE_NAME,
                TermuxContract.RUN_COMMAND_SERVICE,
            )
            action = TermuxContract.ACTION_RUN_COMMAND
            putExtra(TermuxContract.EXTRA_COMMAND_PATH, TermuxContract.BASH_PATH)
            putExtra(TermuxContract.EXTRA_ARGUMENTS, arrayOf("-lc", script))
            putExtra(TermuxContract.EXTRA_WORKDIR, TermuxContract.HOME_PATH)
            putExtra(TermuxContract.EXTRA_BACKGROUND, true)
            putExtra(TermuxContract.EXTRA_COMMAND_LABEL, label)
            putExtra(TermuxContract.EXTRA_COMMAND_DESCRIPTION, description)
            putExtra(TermuxContract.EXTRA_PENDING_INTENT, resultPendingIntent)
        }

        check(context.startService(commandIntent) != null) {
            "Termux RunCommandService başlatılamadı."
        }
        executionId
    }

    private fun nextExecutionId(): Int {
        val next = executionCounter.incrementAndGet()
        if (next <= 0) {
            executionCounter.set(10_000)
            return executionCounter.incrementAndGet()
        }
        return next
    }

    companion object {
        private val executionCounter = AtomicInteger(
            (System.currentTimeMillis() and 0x3fffffff).toInt(),
        )

        private val GUI_RUNTIME_PACKAGES = listOf(
            "gsettings-desktop-schemas",
            "xfwm4",
        )

        internal val SAFE_PROBE_SCRIPT = """
            set +e
            export LC_ALL=C

            emit() {
              key="${'$'}1"
              value="${'$'}2"
              value="${'$'}(printf '%s' "${'$'}value" | tr '\r\n' '  ')"
              printf '%s=%s\n' "${'$'}key" "${'$'}value"
            }

            has_cmd() {
              if command -v "${'$'}1" >/dev/null 2>&1; then
                printf '1'
              else
                printf '0'
              fi
            }

            emit PROBE_VERSION 1
            emit ARCH "${'$'}(uname -m 2>/dev/null)"
            emit WORD_SIZE "${'$'}(getconf LONG_BIT 2>/dev/null)"
            emit OS_SUMMARY "${'$'}(uname -srv 2>/dev/null)"
            emit TERMUX_X11 "${'$'}(has_cmd termux-x11)"
            emit VIRGL "${'$'}(has_cmd virgl_test_server_android)"
            emit GLXINFO "${'$'}(has_cmd glxinfo)"
            emit PROOT_DISTRO "${'$'}(has_cmd proot-distro)"
            emit BOX64 "${'$'}(has_cmd box64)"

            if dpkg-query -W -f='${'$'}{Status}' angle-android 2>/dev/null \
                | grep -q 'install ok installed'; then
              emit ANGLE_ANDROID 1
            else
              emit ANGLE_ANDROID 0
            fi

            if grep -Eq '^[[:space:]]*allow-external-apps[[:space:]]*=[[:space:]]*true[[:space:]]*$' \
                "${'$'}HOME/.termux/termux.properties" 2>/dev/null; then
              emit ALLOW_EXTERNAL_APPS 1
            else
              emit ALLOW_EXTERNAL_APPS 0
            fi
        """.trimIndent()

        private fun installPackageScript(
            packageNames: List<String>,
            displayName: String,
            logName: String,
        ): String {
            require(packageNames.isNotEmpty())
            require(packageNames.all { it.matches(Regex("[a-z0-9+.-]+")) })
            require(displayName.matches(Regex("[A-Za-z0-9 .:+_-]+")))
            val packages = packageNames.joinToString(" ")
            val installedMarker = packageNames.joinToString(",")

            return """
                set +e
                export LC_ALL=C
                MLL_DIR="${'$'}HOME/.matelinuxlauncher"
                mkdir -p "${'$'}MLL_DIR"
                LOG="${'$'}MLL_DIR/$logName"
                PROGRESS="${'$'}MLL_DIR/install-progress.txt"

                write_progress() {
                  stage="${'$'}1"
                  percent="${'$'}2"
                  shift 2
                  message="${'$'}*"
                  message="${'$'}(printf '%s' "${'$'}message" | tr '\r\n' '  ')"
                  tmp="${'$'}PROGRESS.tmp.${'$'}${'$'}"
                  printf 'stage=%s\npercent=%s\nmessage=%s\n' \
                    "${'$'}stage" "${'$'}percent" "${'$'}message" > "${'$'}tmp"
                  mv "${'$'}tmp" "${'$'}PROGRESS"
                }

                write_progress "Hazırlanıyor" "" "$displayName için X11 deposu kontrol ediliyor"
                pkg install x11-repo -y > "${'$'}LOG" 2>&1
                repo_rc="${'$'}?"
                if [ "${'$'}repo_rc" -ne 0 ]; then
                  write_progress "Hata" "" "x11-repo hazırlanamadı"
                  tail -n 30 "${'$'}LOG" 2>/dev/null
                  exit "${'$'}repo_rc"
                fi

                write_progress "Depo güncelleniyor" "" "Paket listeleri yenileniyor"
                apt-get update >> "${'$'}LOG" 2>&1
                update_rc="${'$'}?"
                if [ "${'$'}update_rc" -ne 0 ]; then
                  write_progress "Hata" "" "Paket listeleri güncellenemedi"
                  tail -n 30 "${'$'}LOG" 2>/dev/null
                  exit "${'$'}update_rc"
                fi

                write_progress "Paketler hazırlanıyor" "0" "$displayName bağımlılıkları hesaplanıyor"
                apt-get -y \
                  -o Dpkg::Use-Pty=0 \
                  -o APT::Status-Fd=3 \
                  install $packages \
                  3> >(
                    while IFS=: read -r kind item percent message; do
                      case "${'$'}kind" in
                        dlstatus) stage="İndiriliyor" ;;
                        pmstatus) stage="Kuruluyor" ;;
                        pmerror) stage="Hata" ;;
                        *) stage="İşleniyor" ;;
                      esac
                      pct="${'$'}{percent%%.*}"
                      case "${'$'}pct" in ''|*[!0-9]*) pct="" ;; esac
                      write_progress "${'$'}stage" "${'$'}pct" "${'$'}message"
                    done
                  ) >> "${'$'}LOG" 2>&1
                rc="${'$'}?"

                if [ "${'$'}rc" -eq 0 ]; then
                  write_progress "Tamamlandı" "100" "$displayName kullanıma hazır"
                  echo "PACKAGES_INSTALLED=$installedMarker"
                else
                  write_progress "Hata" "" "$displayName kurulumu tamamlanamadı"
                fi
                tail -n 30 "${'$'}LOG" 2>/dev/null
                exit "${'$'}rc"
            """.trimIndent()
        }
    }
}
