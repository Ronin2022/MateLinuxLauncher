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

    fun startStableX11(): Result<Int> = runScript(
        script = FloatBridgeScripts.START_X11_SCRIPT,
        label = "MateLinuxLauncher X11 oturumu",
        description = "Termux:Float login oturumunda X11 sunucusunu ve uygulama dağıtıcısını başlatır.",
        resultKind = TermuxContract.RESULT_KIND_START_X11,
    )

    fun stopManagedSession(): Result<Int> = runScript(
        script = FloatBridgeScripts.STOP_SESSION_SCRIPT,
        label = "MateLinuxLauncher oturumu kapat",
        description = "Yalnızca MateLinuxLauncher tarafından kaydedilmiş süreçleri durdurur ve wakelock'u bırakır.",
        resultKind = TermuxContract.RESULT_KIND_STOP_SESSION,
    )

    fun installXfceTerminal(): Result<Int> = installPackages(
        packageNames = listOf("gsettings-desktop-schemas", "xfce4-terminal"),
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
        packageNames = listOf("geany"),
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
        packageNames = listOf("gimp"),
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
        packageNames = listOf("libreoffice"),
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
        logName: String,
        resultKind: String,
        label: String,
    ): Result<Int> = runScript(
        script = installPackageScript(packageNames, logName),
        label = label,
        description = "${packageNames.joinToString()} paketlerini Termux X11 deposundan kurar. Çıktı yerel log dosyasına yazılır.",
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

        private fun installPackageScript(packageNames: List<String>, logName: String): String {
            require(packageNames.isNotEmpty())
            require(packageNames.all { it.matches(Regex("[a-z0-9+.-]+")) })
            val packages = packageNames.joinToString(" ")
            val installedMarker = packageNames.joinToString(",")

            return """
                set +e
                export LC_ALL=C
                MLL_DIR="${'$'}HOME/.matelinuxlauncher"
                mkdir -p "${'$'}MLL_DIR"
                LOG="${'$'}MLL_DIR/$logName"
                pkg install x11-repo -y > "${'$'}LOG" 2>&1
                repo_rc="${'$'}?"
                if [ "${'$'}repo_rc" -ne 0 ]; then
                  tail -n 30 "${'$'}LOG" 2>/dev/null
                  exit "${'$'}repo_rc"
                fi
                pkg install $packages -y >> "${'$'}LOG" 2>&1
                rc="${'$'}?"
                tail -n 30 "${'$'}LOG" 2>/dev/null
                if [ "${'$'}rc" -eq 0 ]; then
                  echo "PACKAGES_INSTALLED=$installedMarker"
                fi
                exit "${'$'}rc"
            """.trimIndent()
        }
    }
}
