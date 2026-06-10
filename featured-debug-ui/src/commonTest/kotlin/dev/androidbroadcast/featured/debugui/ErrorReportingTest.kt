package dev.androidbroadcast.featured.debugui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ErrorReportingTest {
    // --- debugUiError ---

    @Test
    fun debugUiError_wrapsMessageAndCause() {
        val cause = RuntimeException("provider failed")
        val error = debugUiError("Failed to build item for 'my_flag'", cause)

        assertIs<IllegalStateException>(error)
        assertTrue(error.message!!.contains("my_flag"))
        assertEquals(cause, error.cause)
    }

    @Test
    fun debugUiError_messageContainsKey() {
        val error = debugUiError("Failed to reset 'some_key'", RuntimeException())
        assertTrue(error.message!!.contains("some_key"))
    }

    // --- reportError ---

    @Test
    fun reportError_invokesCallback() {
        var received: Throwable? = null
        val throwable = debugUiError("Failed to override 'flag'", RuntimeException())

        reportError(onError = { received = it }, throwable = throwable)

        assertEquals(throwable, received)
    }

    @Test
    fun reportError_swallowsExceptionFromCallback() {
        val throwable = debugUiError("Collector died for 'flag'", RuntimeException())

        // A throwing callback must not propagate — the screen must remain functional.
        reportError(
            onError = { throw IllegalStateException("callback blew up") },
            throwable = throwable,
        )
        // If we reach here, the exception was swallowed correctly.
    }

    @Test
    fun reportError_nullReceivedWhenCallbackThrows() {
        var received: Throwable? = null
        val throwable = debugUiError("msg", RuntimeException())

        reportError(
            onError = {
                received = it
                throw RuntimeException("boom")
            },
            throwable = throwable,
        )

        // Callback was invoked before throwing — received is set.
        assertEquals(throwable, received)
    }

    @Test
    fun reportError_noopCallbackIsValid() {
        // Verifies the silence pattern `onError = {}` compiles and does not crash.
        val throwable = debugUiError("msg", RuntimeException())
        reportError(onError = {}, throwable = throwable)
    }
}
