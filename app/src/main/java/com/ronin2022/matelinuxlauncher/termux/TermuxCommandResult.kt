package com.ronin2022.matelinuxlauncher.termux

data class TermuxCommandResult(
    val executionId: Int,
    val kind: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val errorCode: Int,
    val errorMessage: String,
    val receivedAtEpochMs: Long,
)
