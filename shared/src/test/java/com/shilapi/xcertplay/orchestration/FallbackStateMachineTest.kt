package com.shilapi.xcertplay.orchestration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackStateMachineTest {

    @Test
    fun `attempt 1 succeeds directly`() {
        val logs = mutableListOf<String>()
        var attempt2Called = false
        var attempt3Called = false

        val stateMachine = FallbackStateMachine(
            logger = { tag, msg, _ -> logs.add("[$tag] $msg") }
        )

        stateMachine.execute(
            attempt1 = { true },
            attempt2 = { attempt2Called = true; true },
            attempt3 = { attempt3Called = true; true },
        )

        assertEquals(FallbackAttemptState.COMPLETED, stateMachine.currentState)
        assertEquals(false, attempt2Called)
        assertEquals(false, attempt3Called)
        assertTrue(logs.any { it.contains("Attempt 1 (Primary Strategy) succeeded!") })
    }

    @Test
    fun `attempt 1 throws exception, attempt 2 succeeds`() {
        val logs = mutableListOf<String>()
        var attempt3Called = false

        val stateMachine = FallbackStateMachine(
            logger = { tag, msg, _ -> logs.add("[$tag] $msg") }
        )

        stateMachine.execute(
            attempt1 = { throw RuntimeException("Primary strategy crashed") },
            attempt2 = { true },
            attempt3 = { attempt3Called = true; true },
        )

        assertEquals(FallbackAttemptState.COMPLETED, stateMachine.currentState)
        assertEquals(false, attempt3Called)
        assertTrue(logs.any { it.contains("Attempt 1 threw exception: Primary strategy crashed") })
        assertTrue(logs.any { it.contains("Attempt 2 (Secondary Fallback) succeeded!") })
    }

    @Test
    fun `all attempts throw exceptions, app does not crash and sets FAILED_ALL_ATTEMPTS`() {
        val logs = mutableListOf<String>()
        var failureCallbackCalled = false

        val stateMachine = FallbackStateMachine(
            logger = { tag, msg, _ -> logs.add("[$tag] $msg") }
        )

        stateMachine.execute(
            attempt1 = { throw RuntimeException("Attempt 1 error") },
            attempt2 = { throw IllegalStateException("Attempt 2 error") },
            attempt3 = { throw NullPointerException("Attempt 3 error") },
            onFailure = { failureCallbackCalled = true },
        )

        assertEquals(FallbackAttemptState.FAILED_ALL_ATTEMPTS, stateMachine.currentState)
        assertTrue(failureCallbackCalled)
        assertTrue(logs.any { it.contains("All 3 attempt levels failed") })
        assertTrue(logs.all { it.startsWith("[FALLBACK]") })
    }
}
