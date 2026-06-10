package dev.androidbroadcast.featured.debugui

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
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
    fun debugUiError_causeNotReWrapped() {
        // The original cause must be available directly via .cause — debugUiError must
        // not wrap the cause in a second layer of exceptions.
        val original = RuntimeException("root cause")
        val error = debugUiError("Failed to reset 'some_key'", original)
        assertSame(original, error.cause)
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
    fun reportError_swallowsCancellationExceptionFromCallback() {
        // CancellationException thrown BY the callback is also swallowed — the catch
        // clause is `catch (Throwable)`, not `catch (Exception)`. This pins the
        // regression: narrowing to Exception would let CE escape and kill the scope.
        val throwable = debugUiError("msg", RuntimeException())
        reportError(
            onError = { throw CancellationException("callback cancelled") },
            throwable = throwable,
        )
        // If we reach here, the CancellationException was swallowed correctly.
    }

    @Test
    fun reportError_callbackInvokedBeforeThrow() {
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
