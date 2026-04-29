package dev.androidbroadcast.featured.shrinker.harness

import com.android.tools.r8.JdkClassFileProvider
import com.android.tools.r8.OutputMode
import com.android.tools.r8.R8
import com.android.tools.r8.R8Command
import dev.androidbroadcast.featured.shrinker.bytecode.buildBooleanInputJar
import dev.androidbroadcast.featured.shrinker.bytecode.buildIntInputJar
import org.junit.After
import org.junit.Before
import java.io.File
import java.nio.file.Files

/**
 * Base class for R8 dead-code-elimination tests.
 *
 * Creates a temporary working directory before each test and deletes it after.
 * Provides [runBooleanR8] and [runIntR8] helpers that assemble an input JAR,
 * write ProGuard rules, invoke R8 programmatically, and return the output JAR
 * ready for assertion.
 */
internal abstract class R8TestHarness {
    protected lateinit var workDir: File
        private set

    @Before
    fun setup() {
        workDir = Files.createTempDirectory("r8-elimination-test").toFile()
    }

    @After
    fun cleanup() {
        workDir.deleteRecursively()
    }

    /**
     * Builds the boolean input JAR, writes rules via [writeRules], runs R8, and returns
     * the output JAR for assertion.
     */
    protected fun runBooleanR8(writeRules: (File) -> Unit): File = runR8WithJar(::buildBooleanInputJar, writeRules)

    /**
     * Builds the int input JAR, writes rules via [writeRules], runs R8, and returns
     * the output JAR for assertion.
     */
    protected fun runIntR8(writeRules: (File) -> Unit): File = runR8WithJar(::buildIntInputJar, writeRules)

    private fun runR8WithJar(
        buildInputJar: (File) -> Unit,
        writeRules: (File) -> Unit,
    ): File {
        val inputJar = workDir.resolve("input.jar").also(buildInputJar)
        val rulesFile = workDir.resolve("rules.pro").also(writeRules)
        val outputJar = workDir.resolve("output.jar")
        runR8(inputJar, rulesFile, outputJar)
        return outputJar
    }

    private fun runR8(
        inputJar: File,
        rulesFile: File,
        outputJar: File,
    ) {
        R8.run(
            R8Command
                .builder()
                .addProgramFiles(inputJar.toPath())
                .addProguardConfigurationFiles(rulesFile.toPath())
                .addLibraryResourceProvider(JdkClassFileProvider.fromSystemJdk())
                .setOutput(outputJar.toPath(), OutputMode.ClassFile)
                .setDisableMinification(true)
                .build(),
        )
    }
}
