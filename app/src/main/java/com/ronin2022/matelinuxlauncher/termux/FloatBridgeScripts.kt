package com.ronin2022.matelinuxlauncher.termux

/** Huawei/EMUI compatibility bridge for the visible Termux:Float process tree. */
internal object FloatBridgeScripts {

    internal val START_X11_SCRIPT = startX11Script(legacyDrawing = false)
    internal val START_X11_LEGACY_SCRIPT = startX11Script(legacyDrawing = true)

    private fun startX11Script(legacyDrawing: Boolean): String {
        val drawingArg = if (legacyDrawing) " -legacy-drawing" else ""
        val drawingName = if (legacyDrawing) "legacy" else "standard"

        return """
            set +e
            export LC_ALL=C

            MLL_DIR="${'$'}HOME/.matelinuxlauncher"
            PROFILE="${'$'}HOME/.bash_profile"
            PENDING="${'$'}MLL_DIR/float-pending.sh"
            HOOK_MARKER='# >>> MateLinuxLauncher Float bridge >>>'
            mkdir -p "${'$'}MLL_DIR"

            # Stop only processes previously recorded by MateLinuxLauncher.
            for spec in \
              "xfce4-terminal.pid:xfce4-terminal" \
              "geany.pid:geany" \
              "gimp.pid:gimp" \
              "libreoffice-writer.pid:soffice" \
              "xfwm4.pid:xfwm4" \
              "float-dispatcher.pid:bash" \
              "x11.pid:termux-x11"; do
              file="${'$'}{spec%%:*}"
              expected="${'$'}{spec#*:}"
              path="${'$'}MLL_DIR/${'$'}file"
              [ -f "${'$'}path" ] || continue
              old_pid="${'$'}(cat "${'$'}path" 2>/dev/null)"
              if [ -n "${'$'}old_pid" ] && [ -r "/proc/${'$'}old_pid/cmdline" ]; then
                old_cmd="${'$'}(tr '\000' ' ' < "/proc/${'$'}old_pid/cmdline" 2>/dev/null)"
                case "${'$'}old_cmd" in
                  *"${'$'}expected"*) kill "${'$'}old_pid" >/dev/null 2>&1 || true ;;
                esac
              fi
              rm -f "${'$'}path"
            done

            # Do not silently take ownership of a manually-started X server. Tell the launcher to
            # offer the explicit force-reset action instead.
            orphan_x11="${'$'}(pidof termux-x11 2>/dev/null)"
            if [ -n "${'$'}orphan_x11" ]; then
              echo "ORPHAN_X11_DETECTED=${'$'}orphan_x11"
              exit 31
            fi

            rm -f "${'$'}MLL_DIR/float-dispatcher.stop"
            rm -rf "${'$'}MLL_DIR/float-queue"
            mkdir -p "${'$'}MLL_DIR/float-queue"
            (am broadcast -a com.termux.x11.ACTION_STOP -p com.termux.x11 >/dev/null 2>&1 &) || true

            # Bash login shells prefer .bash_profile. Preserve the normal login startup chain if we
            # create it, then append one idempotent Float-only hook.
            if ! grep -Fq "${'$'}HOOK_MARKER" "${'$'}PROFILE" 2>/dev/null; then
              if [ ! -e "${'$'}PROFILE" ]; then
                cat > "${'$'}PROFILE" <<'MLL_PROFILE_BASE'
            if [ -r "${'$'}HOME/.bash_login" ]; then
              . "${'$'}HOME/.bash_login"
            elif [ -r "${'$'}HOME/.profile" ]; then
              . "${'$'}HOME/.profile"
            fi
            MLL_PROFILE_BASE
              fi

              cat >> "${'$'}PROFILE" <<'MLL_PROFILE_HOOK'

            # >>> MateLinuxLauncher Float bridge >>>
            case "${'$'}{TERMUX_APP__FILES_DIR:-}" in
              *com.termux.window*)
                MLL_PENDING="${'$'}HOME/.matelinuxlauncher/float-pending.sh"
                if [ -f "${'$'}MLL_PENDING" ]; then
                  MLL_RUNNING="${'$'}MLL_PENDING.running.${'$'}${'$'}"
                  if mv "${'$'}MLL_PENDING" "${'$'}MLL_RUNNING" 2>/dev/null; then
                    . "${'$'}MLL_RUNNING"
                    rm -f "${'$'}MLL_RUNNING"
                  fi
                fi
                ;;
            esac
            # <<< MateLinuxLauncher Float bridge <<<
            MLL_PROFILE_HOOK
            fi

            cat > "${'$'}PENDING" <<'MLL_FLOAT_PENDING'
            set +e
            export LC_ALL=C
            MLL_DIR="${'$'}HOME/.matelinuxlauncher"
            QUEUE_DIR="${'$'}MLL_DIR/float-queue"
            mkdir -p "${'$'}QUEUE_DIR"
            rm -f "${'$'}MLL_DIR/float-dispatcher.stop"

            mll_float_dispatcher() {
              while [ ! -f "${'$'}MLL_DIR/float-dispatcher.stop" ]; do
                for queued in "${'$'}QUEUE_DIR"/*.pending; do
                  [ -f "${'$'}queued" ] || break
                  base="${'$'}{queued%.pending}"
                  running="${'$'}base.running"
                  result="${'$'}base.result"
                  dispatch_log="${'$'}base.dispatch.log"
                  if mv "${'$'}queued" "${'$'}running" 2>/dev/null; then
                    "${'$'}PREFIX/bin/bash" "${'$'}running" > "${'$'}dispatch_log" 2>&1
                    rc=${'$'}?
                    printf '%s\n' "${'$'}rc" > "${'$'}result.tmp.${'$'}${'$'}"
                    mv "${'$'}result.tmp.${'$'}${'$'}" "${'$'}result"
                    rm -f "${'$'}running"
                  fi
                done
                sleep 0.15
              done
            }

            mll_start_window_manager() {
              command -v xfwm4 >/dev/null 2>&1 || return 0
              attempt=0
              while [ "${'$'}attempt" -lt 5 ]; do
                DISPLAY=:1 xfwm4 --replace > "${'$'}MLL_DIR/xfwm4.log" 2>&1 &
                wpid=${'$'}!
                sleep 1
                if kill -0 "${'$'}wpid" >/dev/null 2>&1; then
                  printf '%s\n' "${'$'}wpid" > "${'$'}MLL_DIR/xfwm4.pid"
                  return 0
                fi
                attempt=${'$'}((attempt + 1))
                sleep 1
              done
              return 0
            }

            mll_start_x11_from_float() {
              termux-wake-lock >/dev/null 2>&1 || true
              if ! command -v termux-x11 >/dev/null 2>&1; then
                printf '%s\n' 'MISSING=termux-x11' > "${'$'}MLL_DIR/float-start.status"
                return 20
              fi

              termux-x11 :1 -dpi 240$drawingArg > "${'$'}MLL_DIR/x11.log" 2>&1 &
              xpid=${'$'}!
              printf '%s\n' "${'$'}xpid" > "${'$'}MLL_DIR/x11.pid"

              mll_float_dispatcher > "${'$'}MLL_DIR/float-dispatcher.log" 2>&1 &
              dpid=${'$'}!
              printf '%s\n' "${'$'}dpid" > "${'$'}MLL_DIR/float-dispatcher.pid"

              sleep 2
              mll_start_window_manager

              printf '%s\n' 'FLOAT_X11_AND_DISPATCHER_LAUNCHED=1' > "${'$'}MLL_DIR/float-start.status"
              printf '%s\n' 'DRAWING_MODE=$drawingName' >> "${'$'}MLL_DIR/float-start.status"
              return 0
            }

            mll_start_x11_from_float
            unset -f mll_start_x11_from_float mll_start_window_manager mll_float_dispatcher 2>/dev/null || true
            MLL_FLOAT_PENDING
            chmod 600 "${'$'}PENDING"
            rm -f "${'$'}MLL_DIR/float-start.status" "${'$'}MLL_DIR/float-pending.sh.running."* 2>/dev/null || true

            if pidof com.termux.window >/dev/null 2>&1; then
              am startservice --user 0 \
                -n com.termux.window/.TermuxFloatService \
                -a com.termux.float.ACTION_STOP_SERVICE >/dev/null 2>&1 || true
              sleep 1
            fi

            echo "FLOAT_PENDING_READY=1"
            i=0
            while [ "${'$'}i" -lt 75 ] && [ -f "${'$'}PENDING" ]; do
              sleep 0.2
              i=${'$'}((i + 1))
            done

            if [ -f "${'$'}PENDING" ]; then
              rm -f "${'$'}PENDING"
              echo "FLOAT_START_TIMEOUT=1"
              exit 30
            fi

            sleep 3
            xpid="${'$'}(cat "${'$'}MLL_DIR/x11.pid" 2>/dev/null)"
            dpid="${'$'}(cat "${'$'}MLL_DIR/float-dispatcher.pid" 2>/dev/null)"
            if [ -n "${'$'}xpid" ] && kill -0 "${'$'}xpid" >/dev/null 2>&1 \
              && [ -n "${'$'}dpid" ] && kill -0 "${'$'}dpid" >/dev/null 2>&1; then
              echo "X11_STARTED_VIA_FLOAT=1"
              echo "DRAWING_MODE=$drawingName"
              exit 0
            fi

            echo "FLOAT_X11_START_FAILED=1"
            tail -n 40 "${'$'}MLL_DIR/x11.log" 2>/dev/null
            exit 21
        """.trimIndent()
    }

    internal val STOP_SESSION_SCRIPT = """
        set +e
        MLL_DIR="${'$'}HOME/.matelinuxlauncher"
        mkdir -p "${'$'}MLL_DIR"
        touch "${'$'}MLL_DIR/float-dispatcher.stop" 2>/dev/null || true
        rm -f "${'$'}MLL_DIR/float-pending.sh" "${'$'}MLL_DIR/float-pending.sh.running."* 2>/dev/null || true
        rm -rf "${'$'}MLL_DIR/float-queue" 2>/dev/null || true

        for f in xfce4-terminal.pid geany.pid gimp.pid libreoffice-writer.pid xfwm4.pid float-dispatcher.pid x11.pid; do
          path="${'$'}MLL_DIR/${'$'}f"
          [ -f "${'$'}path" ] || continue
          pid="${'$'}(cat "${'$'}path" 2>/dev/null)"
          if [ -n "${'$'}pid" ] && kill -0 "${'$'}pid" >/dev/null 2>&1; then
            kill "${'$'}pid" >/dev/null 2>&1 || true
          fi
          rm -f "${'$'}path"
        done

        (am broadcast -a com.termux.x11.ACTION_STOP -p com.termux.x11 >/dev/null 2>&1 &) || true
        (termux-wake-unlock >/dev/null 2>&1 &) || true
        echo "SESSION_STOPPED=1"
        exit 0
    """.trimIndent()

    /** Explicit UAT recovery. Intentionally broader than the normal ownership-preserving stop. */
    internal val FORCE_RESET_SCRIPT = """
        set +e
        MLL_DIR="${'$'}HOME/.matelinuxlauncher"
        mkdir -p "${'$'}MLL_DIR"

        for name in termux-x11 gimp gimp-3.0 geany xfce4-terminal xfwm4 soffice.bin; do
          pkill -x "${'$'}name" >/dev/null 2>&1 || true
        done

        touch "${'$'}MLL_DIR/float-dispatcher.stop" 2>/dev/null || true
        rm -f "${'$'}MLL_DIR"/*.pid \
          "${'$'}MLL_DIR/float-pending.sh" \
          "${'$'}MLL_DIR/float-pending.sh.running."* \
          "${'$'}TMPDIR/.X11-unix/X1" 2>/dev/null || true
        rm -rf "${'$'}MLL_DIR/float-queue" 2>/dev/null || true
        mkdir -p "${'$'}MLL_DIR/float-queue"

        (am broadcast -a com.termux.x11.ACTION_STOP -p com.termux.x11 >/dev/null 2>&1 &) || true
        am startservice --user 0 \
          -n com.termux.window/.TermuxFloatService \
          -a com.termux.float.ACTION_STOP_SERVICE >/dev/null 2>&1 || true
        termux-wake-unlock >/dev/null 2>&1 || true
        echo "FORCE_RESET_COMPLETE=1"
        exit 0
    """.trimIndent()
}