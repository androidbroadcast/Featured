package dev.androidbroadcast.featured.apicompat

import java.io.File

/**
 * Compares a generated Android dump to a committed baseline.
 * This is the check action: it never writes or truncates the baseline.
 */
internal object AndroidApiCheck {
    private val classDeclaration = Regex("""^(?:public|protected)\s.+\{\s*$""")

    fun check(
        baseline: File,
        generatedDump: String,
    ): Result {
        if (!baseline.exists()) {
            return Result.Failed("Android API baseline is missing: ${baseline.path}")
        }
        if (baseline.length() == 0L) {
            return Result.Failed("Android API baseline is zero-length: ${baseline.path}")
        }
        val baselineText = baseline.readText(Charsets.UTF_8)
        if (!hasDeclaration(baselineText)) {
            return Result.Failed("Android API baseline has no declaration: ${baseline.path}")
        }
        if (generatedDump.isBlank() || !hasDeclaration(generatedDump)) {
            return Result.Failed("Generated Android API dump is empty: ${baseline.path}")
        }
        if (baselineText != generatedDump) {
            return Result.Failed(
                "Android API dump differs from ${baseline.path}. The check did not write the baseline.\n" +
                    differingLines(baselineText, generatedDump),
            )
        }
        return Result.Passed
    }

    fun hasDeclaration(text: String): Boolean = text.lineSequence().any { classDeclaration.matches(it) }

    private fun differingLines(
        expected: String,
        actual: String,
    ): String {
        val expectedLines = expected.lines()
        val actualLines = actual.lines()
        val out = StringBuilder()
        var shown = 0
        val limit = 40
        val max = maxOf(expectedLines.size, actualLines.size)
        for (index in 0 until max) {
            val expectedLine = expectedLines.getOrNull(index)
            val actualLine = actualLines.getOrNull(index)
            if (expectedLine == actualLine) {
                continue
            }
            out.append("line ").append(index + 1).append('\n')
            out.append("- ").append(expectedLine ?: "<missing>").append('\n')
            out.append("+ ").append(actualLine ?: "<missing>").append('\n')
            shown++
            if (shown == limit) {
                out.append("... more differences omitted\n")
                break
            }
        }
        return out.toString()
    }

    internal sealed class Result {
        data object Passed : Result()

        data class Failed(
            val message: String,
        ) : Result()
    }
}
