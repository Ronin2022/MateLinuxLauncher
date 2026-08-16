package com.ronin2022.matelinuxlauncher.termux

object TermuxContract {
    const val PACKAGE_NAME = "com.termux"
    const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    const val PERMISSION_RUN_COMMAND = "com.termux.permission.RUN_COMMAND"

    const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
    const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    const val EXTRA_COMMAND_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL"
    const val EXTRA_COMMAND_DESCRIPTION = "com.termux.RUN_COMMAND_COMMAND_DESCRIPTION"
    const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"

    const val RESULT_BUNDLE = "result"
    const val RESULT_STDOUT = "stdout"
    const val RESULT_STDERR = "stderr"
    const val RESULT_EXIT_CODE = "exitCode"
    const val RESULT_ERROR_CODE = "err"
    const val RESULT_ERROR_MESSAGE = "errmsg"

    const val ACTION_COMMAND_RESULT =
        "com.ronin2022.matelinuxlauncher.action.TERMUX_COMMAND_RESULT"
    const val EXTRA_EXECUTION_ID =
        "com.ronin2022.matelinuxlauncher.extra.EXECUTION_ID"
    const val EXTRA_RESULT_KIND =
        "com.ronin2022.matelinuxlauncher.extra.RESULT_KIND"

    const val RESULT_KIND_PROBE = "probe"
    const val RESULT_KIND_START_X11 = "start_x11"
    const val RESULT_KIND_STOP_SESSION = "stop_session"
    const val RESULT_KIND_INSTALL_TERMINAL = "install_terminal"
    const val RESULT_KIND_START_TERMINAL = "start_terminal"
    const val RESULT_KIND_INSTALL_GEANY = "install_geany"
    const val RESULT_KIND_START_GEANY = "start_geany"
    const val RESULT_KIND_INSTALL_GIMP = "install_gimp"
    const val RESULT_KIND_START_GIMP = "start_gimp"
    const val RESULT_KIND_INSTALL_WRITER = "install_writer"
    const val RESULT_KIND_START_WRITER = "start_writer"

    const val BASH_PATH = "/data/data/com.termux/files/usr/bin/bash"
    const val HOME_PATH = "/data/data/com.termux/files/home"
}
