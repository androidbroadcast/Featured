package dev.androidbroadcast.featured.swift

import org.junit.Assume.assumeTrue
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Proves that a local boolean flag with `default = false` makes `generateXcconfig` emit the
 * matching `DISABLE_*` condition, and that compiling a Swift symbol guarded by `#if !DISABLE_*`
 * with only that generated condition removes the symbol.
 *
 * The condition text comes from `build/featured/FeatureFlags.generated.xcconfig`. A missing,
 * different, or extra token fails before compilation. A hand-written `-D` is not a substitute.
 * The same fixture compiled without the generated condition must still keep the symbol, so an
 * always-stripped fixture cannot pass.
 *
 * Hosts without `swiftc` skip the compile. `-PrequireSwiftDce=true` turns that skip into a failure.
 */
internal class SwiftFlagStrippingTest {
    @Test
    fun `default false strips the symbol guarded by the generated condition`() {
        val xcconfig = File(requiredProperty("featured.swiftDce.xcconfig"))
        val text = readGeneratedXcconfig(xcconfig)
        val defines = generatedDisableConditions(text)
        assertEquals(
            listOf(EXPECTED_DISABLE_CONDITION),
            defines,
            "Flag '$FLAG_KEY' must produce exactly $EXPECTED_DISABLE_CONDITION in $xcconfig. " +
                "Got $defines from:\n$text",
        )

        val swiftc = resolveSwiftc() ?: skipOrFail("swiftc was not found on PATH or via xcrun --find swiftc")
        val nm = resolveOnPath("nm") ?: fail("nm was not found on PATH")
        val fixture = File(requiredProperty("featured.swiftDce.fixture"))
        if (!fixture.isFile) {
            fail("Swift fixture is missing: ${fixture.absolutePath}")
        }

        val workDir =
            java.nio.file.Files
                .createTempDirectory("swift-flag-stripping")
                .toFile()
        try {
            val stripped = File(workDir, "stripped.o")
            compile(swiftc, fixture, stripped, defines)
            val strippedSymbols = globalSymbols(nm, stripped)
            if (strippedSymbols.contains(GUARDED_SYMBOL)) {
                fail(
                    "Guarded symbol $GUARDED_SYMBOL is still present after compiling with generated " +
                        "conditions $defines.\n$strippedSymbols",
                )
            }

            val control = File(workDir, "control.o")
            compile(swiftc, fixture, control, defines = emptyList())
            val controlSymbols = globalSymbols(nm, control)
            if (!controlSymbols.contains(GUARDED_SYMBOL)) {
                fail(
                    "Control compile without generated conditions must keep $GUARDED_SYMBOL. " +
                        "The fixture is stripped even when the generated condition is absent.\n$controlSymbols",
                )
            }
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun readGeneratedXcconfig(xcconfig: File): String {
        if (!xcconfig.isFile) {
            fail("generateXcconfig did not produce $xcconfig")
        }
        val text = xcconfig.readText()
        if (text.isBlank()) {
            fail("generateXcconfig wrote a blank xcconfig at $xcconfig")
        }
        return text
    }

    /**
     * Tokens from the single `SWIFT_ACTIVE_COMPILATION_CONDITIONS` assignment, with `$(inherited)`
     * removed. Does not invent a token when the line is missing.
     */
    private fun generatedDisableConditions(text: String): List<String> {
        val assignments =
            text
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("//") }
                .mapNotNull { line ->
                    CONDITIONS_ASSIGNMENT.matchEntire(line)?.groupValues?.get(1)
                }.toList()
        if (assignments.size != 1) {
            fail(
                "Expected exactly one SWIFT_ACTIVE_COMPILATION_CONDITIONS assignment, " +
                    "found ${assignments.size}.\n$text",
            )
        }
        return assignments
            .single()
            .split(WHITESPACE)
            .filter { it.isNotEmpty() && it != INHERITED }
    }

    private fun compile(
        swiftc: String,
        fixture: File,
        output: File,
        defines: List<String>,
    ) {
        val args =
            buildList {
                add(swiftc)
                add("-c")
                add("-O")
                defines.forEach { condition ->
                    add("-D")
                    add(condition)
                }
                add("-o")
                add(output.absolutePath)
                add(fixture.absolutePath)
            }
        val result = runCommand(args)
        if (result.exitCode != 0 || !output.isFile) {
            fail("swiftc failed (${result.exitCode}): ${args.joinToString(" ")}\n${result.output}")
        }
    }

    private fun globalSymbols(
        nm: String,
        objectFile: File,
    ): String {
        val result = runCommand(listOf(nm, "-g", objectFile.absolutePath))
        if (result.exitCode != 0) {
            fail("nm -g failed (${result.exitCode}) for ${objectFile.absolutePath}:\n${result.output}")
        }
        return result.output
    }

    private fun resolveSwiftc(): String? = resolveOnPath("swiftc") ?: xcrunFind("swiftc")

    private fun resolveOnPath(name: String): String? {
        val path = System.getenv("PATH").orEmpty()
        return path
            .split(File.pathSeparator)
            .asSequence()
            .filter { it.isNotEmpty() }
            .map { File(it, name) }
            .firstOrNull { it.isFile && it.canExecute() }
            ?.absolutePath
    }

    private fun xcrunFind(name: String): String? {
        val xcrun = resolveOnPath("xcrun") ?: return null
        val result = runCommand(listOf(xcrun, "--find", name))
        if (result.exitCode != 0) return null
        val found =
            result.output
                .lineSequence()
                .map { it.trim() }
                .lastOrNull { it.isNotEmpty() } ?: return null
        val file = File(found)
        return file.takeIf { it.isFile && it.canExecute() }?.absolutePath
    }

    private fun requiredProperty(name: String): String =
        System.getProperty(name)?.takeIf { it.isNotBlank() }
            ?: fail("Missing system property $name. Run :featured-swift-dce-tests:test so the generated xcconfig is supplied.")

    private fun skipOrFail(reason: String): Nothing {
        if (System.getProperty("featured.swiftDce.require") == "true") {
            fail("$reason (-PrequireSwiftDce=true)")
        }
        assumeTrue(reason, false)
        fail(reason)
    }

    private fun runCommand(args: List<String>): CommandResult {
        val log = File.createTempFile("swift-dce-", ".log")
        return try {
            val process =
                ProcessBuilder(args)
                    .redirectErrorStream(true)
                    .redirectOutput(log)
                    .start()
            val finished = process.waitFor(COMMAND_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            if (!finished) {
                process.destroyForcibly()
                process.waitFor(5, TimeUnit.SECONDS)
            }
            CommandResult(
                exitCode = if (finished) process.exitValue() else TIMEOUT_EXIT,
                output = log.readText(),
            )
        } finally {
            log.delete()
        }
    }

    private data class CommandResult(
        val exitCode: Int,
        val output: String,
    )

    private companion object {
        const val FLAG_KEY = "new_checkout"
        const val EXPECTED_DISABLE_CONDITION = "DISABLE_NEW_CHECKOUT"
        const val GUARDED_SYMBOL = "GuardedCheckoutSymbol"
        const val INHERITED = "\$(inherited)"
        const val COMMAND_TIMEOUT_MINUTES = 2L
        const val TIMEOUT_EXIT = -1
        val CONDITIONS_ASSIGNMENT = Regex("""^SWIFT_ACTIVE_COMPILATION_CONDITIONS\s*=\s*(.*)$""")
        val WHITESPACE = Regex("""\s+""")
    }
}
