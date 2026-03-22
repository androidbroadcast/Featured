package dev.androidbroadcast.featured.firebase

import com.google.android.gms.tasks.Tasks
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class FirebaseConfigValueProviderTest {
    private val remoteConfig: FirebaseRemoteConfig = mockk()
    private val provider = FirebaseConfigValueProvider(remoteConfig)

    @Test
    fun `fetch with activate=true throws FetchException on network failure`() =
        runBlocking {
            val networkError = IOException("Network unavailable")
            every { remoteConfig.fetchAndActivate() } returns Tasks.forException(networkError)

            try {
                provider.fetch(activate = true)
                fail("Expected FetchException to be thrown")
            } catch (e: FetchException) {
                // expected
            }
        }

    @Test
    fun `fetch with activate=false throws FetchException on network failure`() =
        runBlocking {
            val networkError = IOException("Network unavailable")
            every { remoteConfig.fetch() } returns Tasks.forException(networkError)

            try {
                provider.fetch(activate = false)
                fail("Expected FetchException to be thrown")
            } catch (e: FetchException) {
                // expected
            }
        }

    @Test
    fun `FetchException wraps the original cause`() =
        runBlocking {
            val networkError = IOException("Timeout")
            every { remoteConfig.fetchAndActivate() } returns Tasks.forException(networkError)

            try {
                provider.fetch(activate = true)
                fail("Expected FetchException to be thrown")
            } catch (e: FetchException) {
                assertNotNull("FetchException should have a cause", e.cause)
            }
        }

    @Test
    fun `fetch with activate=true succeeds when Firebase succeeds`() =
        runTest {
            every { remoteConfig.fetchAndActivate() } returns Tasks.forResult(true)

            provider.fetch(activate = true)
        }

    @Test
    fun `fetch with activate=false succeeds when Firebase succeeds`() =
        runTest {
            every { remoteConfig.fetch() } returns Tasks.forResult(null)

            provider.fetch(activate = false)
        }
}
