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
        description = "Termux:Float login oturumunu hazırlayıp :1 ekranında yönetilen Termux:X11 sunucusunu oradan başlatır.",
        resultKind = TermuxContract.RESULT_KIND_START_X11,
    )

    fun stopManagedSession(): Result<Int> = runScript(
        script = STOP_SESSION_SCRIPT,
        label = "MateLinuxLauncher oturumu kapat",
        description = "Yalnızca MateLinuxLauncher tarafından kaydedilmiş süreçleri durdurur ve wakelock'u bırakır.",
        resultKind = TermuxContract.RESULT_KIND_STOP_SESSION,
    )

    fun installXfceTerminal(): Result<Int> = installPackage(
        packageName = "xfce4-terminal",
        logName = "install-xfce4-terminal.log",
        resultKind = TermuxContract.RESULT_KIND_INSTALL_TERMINAL,
        label = "XFCE Terminal kur",
    )

    fun startXfceTerminal(): Result<Int> = runScript(
        script = START_TERMINAL_SCRIPT,
        label = "XFCE Terminal aç",
        description = "Yönetilen X11 oturumunda XFCE Terminal'i açar.",
        resultKind = TermuxContract.RESULT_KIND_START_TERMINAL,
    )

    fun installGeany(): Result<Int> = installPackage(
        packageName = "geany",
        logName = "install-geany.log",
        resultKind = TermuxContract.RESULT_KIND_INSTALL_GEANY,
        label = "Geany kur",
    )

    fun startGeany(): Result<Int> = runScript(
        script = START_GEANY_SCRIPT,
        label = "Geany aç",
        description = "Yönetilen X11 oturumunda Geany kod editörünü açar.",
        resultKind = TermuxContract.RESULT_KIND_START_GEANY,
    )

    fun installGimp(): Result<Int> = installPackage(
        packageName = "gimp",
        logName = "install-gimp.log",
        resultKind = TermuxContract.RESULT_KIND_INSTALL_GIMP,
        label = "GIMP kur",
    )

    fun startGimp(): Result<Int> = runScript(
        script = START_GIMP_SCRIPT,
        label = "GIMP aç",
        description = "Yönetilen X11 oturumunda GIMP'i açar.",
        resultKind = TermuxContract.RESULT_KIND_START_GIMP,
    )

    fun installLibreOffice(): Result<Int> = installPackage(
        packageName = "libreoffice",
        logName = "install-libreoffice.log",
        resultKind = TermuxContract.RESULT_KIND_INSTALL_WRITER,
        label = "LibreOffice kur",
    )

    fun startWriter(): Result<Int> = runScript(
        script = START_WRITER_SCRIPT,
        label = "LibreOffice Writer aç",
        description = "Yönetilen X11 oturumunda LibreOffice Writer'ı açar.",
        resultKind = TermuxContract.RESULT_KIND_START_WRITER,
    )

    private fun installPackage(
        packageName: String,
        logName: String,
        resultKind: String,
        label: String,
    ): Result<Int> = runScript(
        script = installPackageScript(packageName, logName),
        label = label,
        description = "$packageName paketini Termux X11 deposundan kurar. Çıktı yerel log dosyasına yazılır.",
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

        private val SESSION_HELPERS = """
            set +e
            export LC_ALL=C
            MLL_DIR="${'$'}HOME/.matelinuxlauncher"
            mkdir -p "${'$'}MLL_DIR"

            pid_alive() {
              [ -n "${'$'}1" ] && kill -0 "${'$'}1" >/dev/null 2>&1
            }

            x11_running() {
              if [ -S "${'$'}TMPDIR/.X11-unix/X1" ]; then
                return 0
              fi
              if [ -f "${'$'}MLL_DIR/x11.pid" ]; then
                p="${'$'}(cat "${'$'}MLL_DIR/x11.pid" 2>/dev/null)"
                pid_alive "${'$'}p" && return 0
              fi
              return 1
            }

            ensure_x11() {
              termux-wake-lock >/dev/null 2>&1 || true
              if x11_running; then
                echo "X11_ALREADY_RUNNING=1"
                return 0
              fi
              if ! command -v termux-x11 >/dev/null 2>&1; then
                echo "MISSING=termux-x11"
                return 20
              fi
              termux-x11 :1 -dpi 240 > "${'$'}MLL_DIR/x11.log" 2>&1 &
              xpid="${'$'}!"
              printf '%s\n' "${'$'}xpid" > "${'$'}MLL_DIR/x11.pid"
              sleep 3
              if x11_running || pid_alive "${'$'}xpid"; then
                echo "X11_STARTED=1"
                return 0
              fi
              echo "X11_START_FAILED=1"
              tail -n 20 "${'$'}MLL_DIR/x11.log" 2>/dev/null
              rm -f "${'$'}MLL_DIR/x11.pid"
              return 21
            }

            verify_app_process() {
              app_pid="${'$'}1"
              app_name="${'$'}2"
              app_log="${'$'}3"
              sleep 2
              if pid_alive "${'$'}app_pid"; then
                echo "APP_STARTED=${'$'}app_name"
                return 0
              fi
              echo "APP_START_FAILED=${'$'}app_name"
              tail -n 40 "${'$'}app_log" 2>/dev/null
              return 22
            }
        """.trimIndent()

        internal val START_X11_SCRIPT = """
            ${'$'}SESSION_HELPERS
            ensure_x11
            exit ${'$'}?
        """.trimIndent().replace("${'$'}SESSION_HELPERS", SESSION_HELPERS)

        internal val START_TERMINAL_SCRIPT = """
            ${'$'}SESSION_HELPERS
            ensure_x11 || exit ${'$'}?
            if ! command -v xfce4-terminal >/dev/null 2>&1; then
              echo "MISSING=xfce4-terminal"
              exit 20
            fi
            log="${'$'}MLL_DIR/xfce4-terminal.log"
            DISPLAY=:1 xfce4-terminal --disable-server --geometry=110x32+30+30 \
              > "${'$'}log" 2>&1 &
            apid="${'$'}!"
            printf '%s\n' "${'$'}apid" > "${'$'}MLL_DIR/xfce4-terminal.pid"
            verify_app_process "${'$'}apid" "xfce4-terminal" "${'$'}log"
            exit ${'$'}?
        """.trimIndent().replace("${'$'}SESSION_HELPERS", SESSION_HELPERS)

        internal val START_GEANY_SCRIPT = launchAppScript(
            command = "geany",
            launch = "dbus-launch --exit-with-session geany",
            pidFile = "geany.pid",
            logFile = "geany.log",
        )

        internal val START_GIMP_SCRIPT = launchAppScript(
            command = "gimp",
            launch = "dbus-launch --exit-with-session gimp",
            pidFile = "gimp.pid",
            logFile = "gimp.log",
        )

        internal val START_WRITER_SCRIPT = """
            ${'$'}SESSION_HELPERS
            ensure_x11 || exit ${'$'}?
            if command -v libreoffice >/dev/null 2>&1; then
              writer=libreoffice
            elif command -v soffice >/dev/null 2>&1; then
              writer=soffice
            else
              echo "MISSING=libreoffice"
              exit 20
            fi
            DISPLAY=:1 dbus-launch --exit-with-session "${'$'}writer" --writer \
              > "${'$'}MLL_DIR/libreoffice-writer.log" 2>&1 &
            apid="${'$'}!"
            printf '%s\n' "${'$'}apid" > "${'$'}MLL_DIR/libreoffice-writer.pid"
            sleep 1
            echo "APP_STARTED=libreoffice-writer"
        """.trimIndent().replace("${'$'}SESSION_HELPERS", SESSION_HELPERS)

        internal val STOP_SESSION_SCRIPT = """
            set +e
            MLL_DIR="${'$'}HOME/.matelinuxlauncher"
            rm -f "${'$'}MLL_DIR/float-pending.sh" "${'$'}MLL_DIR/float-pending.sh.running."* 2>/dev/null || true
            for f in xfce4-terminal.pid geany.pid gimp.pid libreoffice-writer.pid x11.pid; do
              path="${'$'}MLL_DIR/${'$'}f"
              [ -f "${'$'}path" ] || continue
              pid="${'$'}(cat "${'$'}path" 2>/dev/null)"
              if [ -n "${'$'}pid" ] && kill -0 "${'$'}pid" >/dev/null 2>&1; then
                kill "${'$'}pid" >/dev/null 2>&1 || true
              fi
              rm -f "${'$'}path"
            done
            am broadcast -a com.termux.x11.ACTION_STOP -p com.termux.x11 >/dev/null 2>&1 || true
            termux-wake-unlock >/dev/null 2>&1 || true
            echo "SESSION_STOPPED=1"
        """.trimIndent()

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

        private fun launchAppScript(
            command: String,
            launch: String,
            pidFile: String,
            logFile: String,
        ): String = """
            ${'$'}SESSION_HELPERS
            ensure_x11 || exit ${'$'}?
            if ! command -v $command >/dev/null 2>&1; then
              echo "MISSING=$command"
              exit 20
            fi
            log="${'$'}MLL_DIR/$logFile"
            DISPLAY=:1 $launch > "${'$'}log" 2>&1 &
            apid="${'$'}!"
            printf '%s\n' "${'$'}apid" > "${'$'}MLL_DIR/$pidFile"
            verify_app_process "${'$'}apid" "$command" "${'$'}log"
            exit ${'$'}?
        """.trimIndent().replace("${'$'}SESSION_HELPERS", SESSION_HELPERS)

        private fun installPackageScript(packageName: String, logName: String): String = """
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
            pkg install $packageName -y >> "${'$'}LOG" 2>&1
            rc="${'$'}?"
            tail -n 30 "${'$'}LOG" 2>/dev/null
            if [ "${'$'}rc" -eq 0 ]; then
              echo "PACKAGE_INSTALLED=$packageName"
            fi
            exit "${'$'}rc"
        """.trimIndent()
    }
}
