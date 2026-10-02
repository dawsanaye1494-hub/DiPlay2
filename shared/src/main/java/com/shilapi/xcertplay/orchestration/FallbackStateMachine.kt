package com.shilapi.xcertplay.orchestration

import android.util.Log

enum class FallbackAttemptState {
    IDLE,
    ATTEMPT_1_PRIMARY,
    ATTEMPT_2_SECONDARY,
    ATTEMPT_3_EMERGENCY,
    COMPLETED,
    FAILED_ALL_ATTEMPTS
}

/**
 * Stepwise Fallback State Machine (Attempt 1 -> Attempt 2 -> Attempt 3).
 * Wraps every step in try-catch blocks to prevent app crashes and logs all fallback transitions
 * under the [FALLBACK] tag.
 */
class FallbackStateMachine(
    private val logger: ((tag: String, msg: String, isError: Boolean) -> Unit)? = null,
) {
    @Volatile
    var currentState: FallbackAttemptState = FallbackAttemptState.IDLE
        private set

    @Volatile
    var lastError: Throwable? = null
        private set

    /**
     * Executes a stepwise fallback pipeline safely with try-catch blocks and [FALLBACK] logging.
     *
     * @param attempt1 Primary strategy action
     * @param attempt2 Secondary fallback strategy action
     * @param attempt3 Emergency fallback strategy action
     * @param onSuccess Callback when any attempt succeeds
     * @param onFailure Callback if all attempts fail
     */
    fun execute(
        attempt1: () -> Boolean,
        attempt2: () -> Boolean,
        attempt3: () -> Boolean,
        onSuccess: (FallbackAttemptState) -> Unit = {},
        onFailure: (Throwable?) -> Unit = {},
    ) {
        logFallback("Starting Fallback State Machine sequence...")

        // Attempt 1: Primary
        currentState = FallbackAttemptState.ATTEMPT_1_PRIMARY
        logFallback("[FALLBACK] Executing Attempt 1 (Primary Strategy)...")
        try {
            if (attempt1()) {
                currentState = FallbackAttemptState.COMPLETED
                logFallback("[FALLBACK] Attempt 1 (Primary Strategy) succeeded!")
                onSuccess(FallbackAttemptState.ATTEMPT_1_PRIMARY)
                return
            } else {
                logFallback("[FALLBACK] Attempt 1 failed (returned false). Moving to Attempt 2...")
            }
        } catch (e: Throwable) {
            lastError = e
            logFallback("[FALLBACK] Attempt 1 threw exception: ${e.message}. Catching exception & moving to Attempt 2...", isError = true)
        }

        // Attempt 2: Secondary
        currentState = FallbackAttemptState.ATTEMPT_2_SECONDARY
        logFallback("[FALLBACK] Executing Attempt 2 (Secondary Fallback)...")
        try {
            if (attempt2()) {
                currentState = FallbackAttemptState.COMPLETED
                logFallback("[FALLBACK] Attempt 2 (Secondary Fallback) succeeded!")
                onSuccess(FallbackAttemptState.ATTEMPT_2_SECONDARY)
                return
            } else {
                logFallback("[FALLBACK] Attempt 2 failed (returned false). Moving to Attempt 3...")
            }
        } catch (e: Throwable) {
            lastError = e
            logFallback("[FALLBACK] Attempt 2 threw exception: ${e.message}. Catching exception & moving to Attempt 3...", isError = true)
        }

        // Attempt 3: Emergency
        currentState = FallbackAttemptState.ATTEMPT_3_EMERGENCY
        logFallback("[FALLBACK] Executing Attempt 3 (Emergency Fallback)...")
        try {
            if (attempt3()) {
                currentState = FallbackAttemptState.COMPLETED
                logFallback("[FALLBACK] Attempt 3 (Emergency Fallback) succeeded!")
                onSuccess(FallbackAttemptState.ATTEMPT_3_EMERGENCY)
                return
            } else {
                logFallback("[FALLBACK] Attempt 3 failed (returned false). All attempts exhausted.")
            }
        } catch (e: Throwable) {
            lastError = e
            logFallback("[FALLBACK] Attempt 3 threw exception: ${e.message}. Catching exception. All attempts exhausted.", isError = true)
        }

        // All failed safely without crashing
        currentState = FallbackAttemptState.FAILED_ALL_ATTEMPTS
        logFallback("[FALLBACK] All 3 attempt levels failed. State set to FAILED_ALL_ATTEMPTS.", isError = true)
        onFailure(lastError)
    }

    private fun logFallback(msg: String, isError: Boolean = false) {
        if (logger != null) {
            logger.invoke("FALLBACK", msg, isError)
        } else {
            try {
                if (isError) {
                    Log.e("FALLBACK", msg)
                } else {
                    Log.d("FALLBACK", msg)
                }
            } catch (_: Throwable) {
                println("[FALLBACK] $msg")
            }
        }
    }
}
