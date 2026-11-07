package dev.androidbroadcast.featured.apicompat

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AndroidApiCheckTest {
    @Test
    fun `matching dump and baseline passes and does not write the baseline`() {
        val baseline = tempBaseline(DUMP)
        val before = baseline.readBytes()
        val result = AndroidApiCheck.check(baseline, DUMP)
        assertIs<AndroidApiCheck.Result.Passed>(result)
        assertEquals(before.toList(), baseline.readBytes().toList())
    }

    @Test
    fun `missing baseline fails and is not created`() {
        val baseline = Files.createTempDirectory("android-api-missing").resolve("missing.api").toFile()
        val result = AndroidApiCheck.check(baseline, DUMP)
        val failed = assertIs<AndroidApiCheck.Result.Failed>(result)
        assertTrue(failed.message.contains("missing"), failed.message)
        assertTrue(!baseline.exists())
    }

    @Test
    fun `zero-length baseline fails and is not truncated further`() {
        val baseline = tempBaseline("")
        val result = AndroidApiCheck.check(baseline, DUMP)
        val failed = assertIs<AndroidApiCheck.Result.Failed>(result)
        assertTrue(failed.message.contains("zero-length"), failed.message)
        assertEquals(0, baseline.length())
    }

    @Test
    fun `baseline with no declaration fails even when the texts match`() {
        val text = "not a dump\n"
        val baseline = tempBaseline(text)
        val before = baseline.readBytes()
        val result = AndroidApiCheck.check(baseline, text)
        val failed = assertIs<AndroidApiCheck.Result.Failed>(result)
        assertTrue(failed.message.contains("no declaration"), failed.message)
        assertEquals(before.toList(), baseline.readBytes().toList())
    }

    @Test
    fun `any difference fails including one added signature line and does not write the baseline`() {
        val baseline = tempBaseline(DUMP)
        val before = baseline.readBytes()
        val generated =
            DUMP.replace(
                "\tpublic final fun existing ()V\n",
                "\tpublic final fun added ()V\n\tpublic final fun existing ()V\n",
            )
        assertTrue(generated != DUMP)
        val result = AndroidApiCheck.check(baseline, generated)
        val failed = assertIs<AndroidApiCheck.Result.Failed>(result)
        assertTrue(failed.message.contains("differs"), failed.message)
        assertTrue(failed.message.contains("added"), failed.message)
        assertEquals(before.toList(), baseline.readBytes().toList())
    }

    @Test
    fun `empty generated dump fails and does not write the baseline`() {
        val baseline = tempBaseline(DUMP)
        val before = baseline.readBytes()
        val result = AndroidApiCheck.check(baseline, "")
        val failed = assertIs<AndroidApiCheck.Result.Failed>(result)
        assertTrue(failed.message.contains("empty"), failed.message)
        assertEquals(before.toList(), baseline.readBytes().toList())
    }

    private fun tempBaseline(text: String) =
        Files.createTempFile("android-api-baseline", ".api").toFile().apply {
            writeText(text, Charsets.UTF_8)
            deleteOnExit()
        }

    private companion object {
        val DUMP: String =
            """
            public final class fixture/Example {
            	public final fun existing ()V
            }

            """.trimIndent() + "\n"
    }
}
