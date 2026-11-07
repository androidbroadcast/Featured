package dev.androidbroadcast.featured.apicompat

import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JvmAbiDumperTest {
    @Test
    fun `default dump keeps public and protected JVM descriptors and drops internal and private`() {
        val classes = compile(PUBLIC_AND_HIDDEN)
        val dump = dump(classes)
        assertFalse(dump.contains("internal "), dump)
        assertTrue(dump.contains("class fixture/PublicApi {"), dump)
        assertTrue(dump.contains("fun visible ()Ljava/lang/String;"), dump)
        assertTrue(dump.contains("protected"), dump)
        assertTrue(dump.contains("alsoVisible"), dump)
        assertFalse(dump.contains("hidden"), dump)
        assertFalse(dump.contains("InternalOnly"), dump)
        assertFalse(dump.contains("secret"), dump)
        assertFalse(dump.contains("nope"), dump)
    }

    @Test
    fun `comment and KDoc do not change the dump`() {
        val documented =
            compile(
                """
                package fixture

                /** first documentation */
                public class Documented {
                    public fun value(): String = "ok"
                }
                """.trimIndent(),
            )
        val commented =
            compile(
                """
                package fixture

                /** second documentation */
                public class Documented {
                    // comment only
                    public fun value(): String = "ok"
                }
                """.trimIndent(),
            )
        assertEquals(dump(documented), dump(commented))
    }

    @Test
    fun `added public method changes the dump and is not auto-accepted`() {
        val original = dump(compile(PUBLIC_AND_HIDDEN))
        val added =
            dump(
                compile(
                    """
                    package fixture

                    public open class PublicApi {
                        public fun visible(): String = "ok"
                        public fun added(): String = "extra"
                        protected fun alsoVisible(): Int = 1
                        internal fun hidden(): String = "no"
                        private fun secret(): String = "no"
                    }
                    """.trimIndent(),
                ),
            )
        assertTrue(added.contains("added"), added)
        val baseline = Files.createTempFile("fixture-baseline", ".api").toFile()
        baseline.writeText(original, Charsets.UTF_8)
        val before = baseline.readBytes()
        val failed = AndroidApiCheck.check(baseline, added)
        assertIs<AndroidApiCheck.Result.Failed>(failed)
        assertEquals(before.toList(), baseline.readBytes().toList())

        baseline.writeText(added, Charsets.UTF_8)
        assertIs<AndroidApiCheck.Result.Passed>(AndroidApiCheck.check(baseline, added))
    }

    private fun dump(classes: File): String {
        val classpath =
            System
                .getProperty("android.api.compat.tools")
                ?.split(File.pathSeparator)
                ?.filter { it.isNotEmpty() }
                ?.map { File(it).toPath() }
                .orEmpty()
        assertTrue(classpath.isNotEmpty(), "android.api.compat.tools was not set")
        return JvmAbiDumper.dump(JvmAbiDumper.collectClassInputs(listOf(classes)), classpath)
    }

    private fun compile(source: String): File {
        val root = Files.createTempDirectory("android-api-fixture").toFile()
        val sourceFile = File(root, "Fixture.kt").apply { writeText(source, Charsets.UTF_8) }
        val output = File(root, "classes").apply { mkdirs() }
        val messages = ByteArrayOutputStream()
        val collector = PrintingMessageCollector(PrintStream(messages), MessageRenderer.PLAIN_FULL_PATHS, false)
        val compiler = K2JVMCompiler()
        val stdlib =
            File(
                Unit::class.java.protectionDomain.codeSource.location
                    .toURI(),
            )
        val arguments: K2JVMCompilerArguments =
            compiler.createArguments().apply {
                freeArgs = listOf(sourceFile.absolutePath)
                destination = output.absolutePath
                classpath = stdlib.absolutePath
                noStdlib = true
                noReflect = true
                jdkHome = System.getProperty("java.home")
            }
        val exit = compiler.exec(collector, Services.EMPTY, arguments)
        assertEquals(ExitCode.OK, exit, messages.toString(Charsets.UTF_8))
        return output
    }

    private companion object {
        val PUBLIC_AND_HIDDEN: String =
            """
            package fixture

            public open class PublicApi {
                public fun visible(): String = "ok"
                protected fun alsoVisible(): Int = 1
                internal fun hidden(): String = "no"
                private fun secret(): String = "no"
            }

            internal class InternalOnly {
                public fun nope(): String = "no"
            }
            """.trimIndent()
    }
}
