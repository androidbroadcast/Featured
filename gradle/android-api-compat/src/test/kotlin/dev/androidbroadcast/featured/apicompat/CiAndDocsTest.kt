package dev.androidbroadcast.featured.apicompat

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CiAndDocsTest {
    @Test
    fun `ci runs the android gate on linux pull requests and does not update baselines`() {
        val workflow = repoFile(".github/workflows/ci.yml").readText()
        assertTrue(workflow.contains("pull_request:"), workflow)
        assertTrue(workflow.contains("branches: [ main, develop ]") || workflow.contains("develop"), workflow)
        assertTrue(workflow.contains("main"), workflow)
        val pullRequest = workflow.substringAfter("pull_request:").substringBefore("permissions:")
        assertTrue(pullRequest.contains("develop"), pullRequest)
        assertTrue(pullRequest.contains("main"), pullRequest)

        val job = jobBody(workflow, "android-api-compat")
        assertTrue(job.contains("name: Android API compatibility"), job)
        assertTrue(job.contains("runs-on: ubuntu-latest"), job)
        assertTrue(job.contains("checkAndroidApi"), job)
        assertTrue(job.contains(":android-api-compat:test"), job)
        assertFalse(job.contains("updateAndroidApi"), job)
        assertFalse(job.contains("apiCheck"), job)
        assertFalse(job.contains("checkKotlinAbi"), job)
        assertFalse(job.contains("IosArm64"), job)
        assertFalse(job.contains("IosSimulator"), job)
        assertFalse(job.contains("xcodebuild"), job)
        assertFalse(job.contains("macos"), job)

        val ios = jobBody(workflow, "build-ios")
        assertTrue(ios.contains("runs-on: macos-latest"), ios)
        assertFalse(ios.contains("checkAndroidApi"), ios)
    }

    @Test
    fun `docs name the android gate and keep the preview exception`() {
        val contributing = repoFile("CONTRIBUTING.md").readText()
        val claude = repoFile("CLAUDE.md").readText()
        val agents = repoFile("AGENTS.md").readText()
        listOf(contributing, claude).forEach { text ->
            assertFalse(text.contains("reviewed manually"), text.take(80))
            assertFalse(text.contains("manual only"), text.take(80))
            assertFalse(text.contains("no automated"), text.take(80))
            assertTrue(text.contains("checkAndroidApi"), text.take(80))
            assertTrue(text.contains("docs/specs/api-compat-gate.md"))
            assertTrue(text.contains("iOS and JVM public API may change in a minor release without a major bump"))
            assertFalse(text.contains("gate computes the version bump"))
            assertTrue(text.contains("does not compute the version bump") || text.contains("does not select the version bump"))
        }
        assertFalse(agents.contains("compatibility is reviewed manually"))
        assertFalse(agents.contains("reviewed manually"))
    }

    private fun jobBody(
        workflow: String,
        jobKey: String,
    ): String {
        val start = workflow.indexOf("\n  $jobKey:")
        assertTrue(start >= 0, "missing job $jobKey")
        val rest = workflow.substring(start + 1)
        val next = Regex("(?m)^  [a-z0-9-]+:").find(rest.substring(1))
        return if (next == null) rest else rest.substring(0, next.range.first + 1)
    }

    private fun repoFile(relative: String): File {
        val root = File(System.getProperty("featured.repoRoot") ?: error("featured.repoRoot was not set"))
        return File(root, relative).also { assertTrue(it.isFile, it.path) }
    }
}
