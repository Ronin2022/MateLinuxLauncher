package com.ronin2022.matelinuxlauncher.termux

/** Fixed Linux application launch requests consumed by the Float-resident dispatcher. */
internal object FloatAppScripts {

    internal val START_TERMINAL_SCRIPT = enqueueAppScript(
        requiredCommand = "xfce4-terminal",
        appName = "xfce4-terminal",
        logFile = "xfce4-terminal.log",
        pidFile = "xfce4-terminal.pid",
        preflightBody = """
            if ! dpkg-query -W -f='${'$'}{Status}' gsettings-desktop-schemas 2>/dev/null \
              | grep -q 'install ok installed'; then
              echo "MISSING=gsettings-desktop-schemas"
              exit 20
            fi
        """.trimIndent(),
        launchBody = """
            DISPLAY=:1 GDK_BACKEND=x11 xfce4-terminal --disable-server --geometry=110x32+30+30 \
              > "${'$'}log" 2>&1 &
        """.trimIndent(),
    )

    internal val START_GEANY_SCRIPT = enqueueAppScript(
        requiredCommand = "geany",
        appName = "geany",
        logFile = "geany.log",
        pidFile = "geany.pid",
        launchBody = """
            DISPLAY=:1 GDK_BACKEND=x11 geany > "${'$'}log" 2>&1 &
        """.trimIndent(),
    )

    internal val START_GIMP_SCRIPT = enqueueAppScript(
        requiredCommand = "gimp",
        appName = "gimp",
        logFile = "gimp.log",
        pidFile = "gimp.pid",
        launchBody = """
            DISPLAY=:1 GDK_BACKEND=x11 gimp > "${'$'}log" 2>&1 &
        """.trimIndent(),
    )

    internal val START_WRITER_SCRIPT = enqueueAppScript(
        requiredCommand = "libreoffice",
        appName = "libreoffice-writer",
        logFile = "libreoffice-writer.log",
        pidFile = "libreoffice-writer.pid",
        launchBody = """
            if command -v libreoffice >/dev/null 2>&1; then
              writer=libreoffice
            elif command -v soffice >/dev/null 2>&1; then
              writer=soffice
            else
              echo "MISSING=libreoffice" >&2
              exit 20
            fi
            DISPLAY=:1 "${'$'}writer" --writer > "${'$'}log" 2>&1 &
        """.trimIndent(),
        alternateCommand = "soffice",
    )

    private fun enqueueAppScript(
        requiredCommand: String,
        appName: String,
        logFile: String,
        pidFile: String,
        launchBody: String,
        alternateCommand: String? = null,
        preflightBody: String = "",
    ): String {
        val commandCheck = if (alternateCommand == null) {
            "command -v $requiredCommand >/dev/null 2>&1"
        } else {
            "command -v $requiredCommand >/dev/null 2>&1 || command -v $alternateCommand >/dev/null 2>&1"
        }

        val requestBody = """
            set +e
            MLL_DIR="${'$'}HOME/.matelinuxlauncher"
            log="${'$'}MLL_DIR/$logFile"
            export XDG_RUNTIME_DIR="${'$'}TMPDIR/mll-runtime"
            mkdir -p "${'$'}XDG_RUNTIME_DIR"
            chmod 700 "${'$'}XDG_RUNTIME_DIR" 2>/dev/null || true

            $preflightBody
            $launchBody
            apid=${'$'}!
            printf '%s\n' "${'$'}apid" > "${'$'}MLL_DIR/$pidFile"
            sleep 2
            if [ -n "${'$'}apid" ] && kill -0 "${'$'}apid" >/dev/null 2>&1; then
              exit 0
            fi
            echo "APP_PROCESS_DIED=$appName" >&2
            tail -n 50 "${'$'}log" 2>/dev/null >&2
            exit 22
        """.trimIndent()

        return """
            set +e
            export LC_ALL=C
            MLL_DIR="${'$'}HOME/.matelinuxlauncher"
            QUEUE_DIR="${'$'}MLL_DIR/float-queue"
            mkdir -p "${'$'}QUEUE_DIR"

            if ! ($commandCheck); then
              echo "MISSING=$requiredCommand"
              exit 20
            fi

            xpid="${'$'}(cat "${'$'}MLL_DIR/x11.pid" 2>/dev/null)"
            if [ -z "${'$'}xpid" ] || ! kill -0 "${'$'}xpid" >/dev/null 2>&1; then
              echo "FLOAT_X11_NOT_RUNNING=1"
              exit 23
            fi

            dpid="${'$'}(cat "${'$'}MLL_DIR/float-dispatcher.pid" 2>/dev/null)"
            if [ -z "${'$'}dpid" ] || ! kill -0 "${'$'}dpid" >/dev/null 2>&1; then
              echo "FLOAT_DISPATCHER_NOT_RUNNING=1"
              exit 24
            fi

            token="$appName-${'$'}${'$'}-${'$'}(date +%s)"
            base="${'$'}QUEUE_DIR/${'$'}token"
            request="${'$'}base.pending"
            result="${'$'}base.result"
            dispatch_log="${'$'}base.dispatch.log"
            tmp="${'$'}request.tmp.${'$'}${'$'}"

            cat > "${'$'}tmp" <<'MLL_APP_REQUEST'
$requestBody
MLL_APP_REQUEST
            chmod 700 "${'$'}tmp"
            mv "${'$'}tmp" "${'$'}request"

            i=0
            while [ "${'$'}i" -lt 80 ] && [ ! -f "${'$'}result" ]; do
              sleep 0.15
              i=${'$'}((i + 1))
            done

            if [ ! -f "${'$'}result" ]; then
              echo "FLOAT_APP_TIMEOUT=$appName"
              exit 25
            fi

            rc="${'$'}(cat "${'$'}result" 2>/dev/null)"
            rm -f "${'$'}result"
            if [ "${'$'}rc" = "0" ]; then
              echo "APP_STARTED=$appName"
              rm -f "${'$'}dispatch_log" 2>/dev/null || true
              exit 0
            fi

            echo "APP_START_FAILED=$appName"
            tail -n 60 "${'$'}dispatch_log" 2>/dev/null
            rm -f "${'$'}dispatch_log" 2>/dev/null || true
            case "${'$'}rc" in
              ''|*[!0-9]*) exit 22 ;;
              *) exit "${'$'}rc" ;;
            esac
        """.trimIndent()
    }
}
