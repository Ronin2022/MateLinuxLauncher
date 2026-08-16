package com.ronin2022.matelinuxlauncher.termux

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class TermuxResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val resultBundle = intent.getBundleExtra(TermuxContract.RESULT_BUNDLE) ?: return
        val result = TermuxCommandResult(
            executionId = intent.getIntExtra(TermuxContract.EXTRA_EXECUTION_ID, -1),
            stdout = resultBundle.getString(TermuxContract.RESULT_STDOUT).orEmpty(),
            stderr = resultBundle.getString(TermuxContract.RESULT_STDERR).orEmpty(),
            exitCode = resultBundle.getInt(TermuxContract.RESULT_EXIT_CODE, -1),
            errorCode = resultBundle.getInt(TermuxContract.RESULT_ERROR_CODE, Int.MIN_VALUE),
            errorMessage = resultBundle.getString(TermuxContract.RESULT_ERROR_MESSAGE).orEmpty(),
            receivedAtEpochMs = System.currentTimeMillis(),
        )
        TermuxResultStore(context.applicationContext).save(result)
        TermuxResultBus.publish(result)
    }
}

object TermuxResultBus {
    private val mutableResults = MutableSharedFlow<TermuxCommandResult>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val results = mutableResults.asSharedFlow()

    fun publish(result: TermuxCommandResult) {
        mutableResults.tryEmit(result)
    }
}
