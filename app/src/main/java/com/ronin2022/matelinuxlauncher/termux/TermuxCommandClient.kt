package com.ronin2022.matelinuxlauncher.termux

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.concurrent.atomic.AtomicInteger

class TermuxCommandClient(private val context: Context) {

    fun runSafeEnvironmentProbe(): Result<Int> = runCatching {
        val executionId = nextExecutionId()
        val resultIntent = Intent(context, TermuxResultReceiver::class.java).apply {
            action = TermuxContract.ACTION_COMMAND_RESULT
            putExtra(TermuxContract.EXTRA_EXECUTION_ID, executionId)
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
            putExtra(TermuxContract.EXTRA_ARGUMENTS, arrayOf("-lc", SAFE_PROBE_SCRIPT))
            putExtra(TermuxContract.EXTRA_WORKDIR, TermuxContract.HOME_PATH)
            putExtra(TermuxContract.EXTRA_BACKGROUND, true)
            putExtra(
                TermuxContract.EXTRA_COMMAND_LABEL,
                "MateLinuxLauncher güvenli ortam teşhisi",
            )
            putExtra(
                TermuxContract.EXTRA_COMMAND_DESCRIPTION,
                "Yalnızca mimariyi ve seçili Linux/X11 komutlarının varlığını okur.",
            )
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
    }
}
