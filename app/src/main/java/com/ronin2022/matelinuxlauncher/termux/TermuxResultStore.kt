package com.ronin2022.matelinuxlauncher.termux

import android.content.Context

class TermuxResultStore(context: Context) {

    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun save(result: TermuxCommandResult) {
        preferences.edit()
            .putInt(KEY_EXECUTION_ID, result.executionId)
            .putString(KEY_STDOUT, result.stdout.take(MAX_STORED_TEXT))
            .putString(KEY_STDERR, result.stderr.take(MAX_STORED_TEXT))
            .putInt(KEY_EXIT_CODE, result.exitCode)
            .putInt(KEY_ERROR_CODE, result.errorCode)
            .putString(KEY_ERROR_MESSAGE, result.errorMessage.take(MAX_STORED_TEXT))
            .putLong(KEY_RECEIVED_AT, result.receivedAtEpochMs)
            .apply()
    }

    fun load(): TermuxCommandResult? {
        if (!preferences.contains(KEY_RECEIVED_AT)) return null
        return TermuxCommandResult(
            executionId = preferences.getInt(KEY_EXECUTION_ID, -1),
            stdout = preferences.getString(KEY_STDOUT, "").orEmpty(),
            stderr = preferences.getString(KEY_STDERR, "").orEmpty(),
            exitCode = preferences.getInt(KEY_EXIT_CODE, -1),
            errorCode = preferences.getInt(KEY_ERROR_CODE, Int.MIN_VALUE),
            errorMessage = preferences.getString(KEY_ERROR_MESSAGE, "").orEmpty(),
            receivedAtEpochMs = preferences.getLong(KEY_RECEIVED_AT, 0L),
        )
    }

    companion object {
        private const val PREFERENCES = "termux_result_store"
        private const val KEY_EXECUTION_ID = "execution_id"
        private const val KEY_STDOUT = "stdout"
        private const val KEY_STDERR = "stderr"
        private const val KEY_EXIT_CODE = "exit_code"
        private const val KEY_ERROR_CODE = "error_code"
        private const val KEY_ERROR_MESSAGE = "error_message"
        private const val KEY_RECEIVED_AT = "received_at"
        private const val MAX_STORED_TEXT = 32_768
    }
}
