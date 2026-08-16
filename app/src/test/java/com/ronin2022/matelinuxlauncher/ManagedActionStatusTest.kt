package com.ronin2022.matelinuxlauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class ManagedActionStatusTest {

    @Test
    fun runningActionIsRecoveredAfterDeadline() {
        val status = ManagedActionStatus(
            phase = ManagedActionPhase.RUNNING,
            message = "çalışıyor",
            executionId = 42,
            startedAtEpochMs = 1_000L,
            timeoutMs = 5_000L,
        )

        val recovered = MainViewModel.recoverStaleManagedAction(status, 6_001L)

        assertEquals(ManagedActionPhase.ERROR, recovered.phase)
        assertEquals(42, recovered.executionId)
    }

    @Test
    fun runningActionStaysRunningBeforeDeadline() {
        val status = ManagedActionStatus(
            phase = ManagedActionPhase.RUNNING,
            executionId = 7,
            startedAtEpochMs = 1_000L,
            timeoutMs = 5_000L,
        )

        val recovered = MainViewModel.recoverStaleManagedAction(status, 5_999L)

        assertEquals(ManagedActionPhase.RUNNING, recovered.phase)
    }

    @Test
    fun legacyRunningStateCannotLockUiForever() {
        val status = ManagedActionStatus(
            phase = ManagedActionPhase.RUNNING,
            executionId = 9,
        )

        val recovered = MainViewModel.recoverStaleManagedAction(status, 10_000L)

        assertEquals(ManagedActionPhase.ERROR, recovered.phase)
    }
}
