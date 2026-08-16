package com.ronin2022.matelinuxlauncher.termux

/**
 * Huawei/EMUI compatibility bridge.
 *
 * Termux:X11 is substantially more stable on the MRDI-W09 when the X server is created from the
 * visible Termux:Float login session instead of from a short-lived RUN_COMMAND shell. This script
 * installs a small, idempotent bash-profile hook that only activates for the Termux:Float package,
 * writes one pending X11-start request, asks an existing Float service to stop, and waits for the
 * newly opened Float login shell to consume the request.
 */
internal object FloatBridgeScripts {

    internal val START_X11_SCRIPT = """
        set +e
        export LC_ALL=C

        MLL_DIR="${'$'}HOME/.matelinuxlauncher"
        PROFILE="${'$'}HOME/.bash_profile"
        PENDING="${'$'}MLL_DIR/float-pending.sh"
        HOOK_MARKER='# >>> MateLinuxLauncher Float bridge >>>'
        mkdir -p "${'$'}MLL_DIR"

        # Clean only a previously recorded MateLinuxLauncher X11 process.
        if [ -f "${'$'}MLL_DIR/x11.pid" ]; then
          old_pid="${'$'}(cat "${'$'}MLL_DIR/x11.pid" 2>/dev/null)"
          if [ -n "${'$'}old_pid" ] && [ -r "/proc/${'$'}old_pid/cmdline" ]; then
            old_cmd="${'$'}(tr '\000' ' ' < "/proc/${'$'}old_pid/cmdline" 2>/dev/null)"
            case "${'$'}old_cmd" in
              *termux-x11*) kill "${'$'}old_pid" >/dev/null 2>&1 || true ;;
            esac
          fi
          rm -f "${'$'}MLL_DIR/x11.pid"
        fi
        am broadcast -a com.termux.x11.ACTION_STOP -p com.termux.x11 >/dev/null 2>&1 || true

        # Bash login shells prefer .bash_profile over .bash_login/.profile. If we have to create it,
        # preserve the startup file bash would otherwise have sourced.
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
# Only execute the pending request in Termux:Float. Regular Termux login shells ignore it.
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

        # The pending file is sourced by the next Termux:Float login shell. X11 therefore becomes a
        # child of the visible Float session, matching the live UAT sequence that stayed stable.
        cat > "${'$'}PENDING" <<'MLL_FLOAT_PENDING'
set +e
export LC_ALL=C
MLL_DIR="${'$'}HOME/.matelinuxlauncher"
mkdir -p "${'$'}MLL_DIR"

mll_start_x11_from_float() {
  termux-wake-lock >/dev/null 2>&1 || true

  if ! command -v termux-x11 >/dev/null 2>&1; then
    printf '%s\n' 'MISSING=termux-x11' > "${'$'}MLL_DIR/float-start.status"
    return 20
  fi

  termux-x11 :1 -dpi 240 > "${'$'}MLL_DIR/x11.log" 2>&1 &
  xpid=${'$'}!
  printf '%s\n' "${'$'}xpid" > "${'$'}MLL_DIR/x11.pid"
  printf '%s\n' 'FLOAT_X11_LAUNCHED=1' > "${'$'}MLL_DIR/float-start.status"
  return 0
}

mll_start_x11_from_float
unset -f mll_start_x11_from_float 2>/dev/null || true
MLL_FLOAT_PENDING
        chmod 600 "${'$'}PENDING"
        rm -f "${'$'}MLL_DIR/float-start.status" "${'$'}MLL_DIR/float-pending.sh.running."* 2>/dev/null || true

        # If Float is already alive, restart only its own service so the login hook runs again.
        # Termux and Termux:Float share the same Android UID in the official GitHub builds.
        if pidof com.termux.window >/dev/null 2>&1; then
          am startservice --user 0 \
            -n com.termux.window/.TermuxFloatService \
            -a com.termux.float.ACTION_STOP_SERVICE >/dev/null 2>&1 || true
          sleep 1
        fi

        echo "FLOAT_PENDING_READY=1"

        # MateLinuxLauncher opens Termux:Float from the foreground shortly after this script starts.
        # Wait for the Float login shell to move/source the pending request.
        i=0
        while [ "${'$'}i" -lt 60 ] && [ -f "${'$'}PENDING" ]; do
          sleep 0.2
          i=${'$'}((i + 1))
        done

        if [ -f "${'$'}PENDING" ]; then
          echo "Termux:Float yeni oturumu zamanında başlamadı." >&2
          echo "FLOAT_START_TIMEOUT=1"
          exit 30
        fi

        sleep 3
        xpid="${'$'}(cat "${'$'}MLL_DIR/x11.pid" 2>/dev/null)"
        if [ -n "${'$'}xpid" ] && kill -0 "${'$'}xpid" >/dev/null 2>&1; then
          echo "X11_STARTED_VIA_FLOAT=1"
          exit 0
        fi

        echo "Termux:X11 Float oturumundan başlatıldı fakat süreç ayakta kalmadı." >&2
        tail -n 40 "${'$'}MLL_DIR/x11.log" 2>/dev/null
        exit 21
    """.trimIndent()
}
