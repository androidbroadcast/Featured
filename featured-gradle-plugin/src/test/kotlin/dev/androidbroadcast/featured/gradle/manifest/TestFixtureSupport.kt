package dev.androidbroadcast.featured.gradle.manifest

import java.io.File

// Shared helpers for the manifest test suite. Each integration / TestKit test copies a
// pinned fixture directory into a per-test temp folder so that test runs do not pollute
// the source tree and remain isolated from each other.

/**
 * Copies the fixture directory named [fixtureName] from `featured-gradle-plugin/src/test/fixtures/`
 * into [dest]. Non-file entries are skipped. The `.gitkeep` marker files used to keep otherwise
 * empty fixture directories in git are filtered out — they are not part of the project under test.
 */
internal fun copyManifestFixture(
    fixtureName: String,
    dest: File,
) {
    val source = fixtureSourceDir(fixtureName)
    source
        .walkTopDown()
        .filter { it.isFile && it.name != ".gitkeep" }
        .forEach { file ->
            val target = dest.resolve(file.relativeTo(source))
            target.parentFile?.mkdirs()
            file.copyTo(target, overwrite = true)
        }
}

private fun fixtureSourceDir(fixtureName: String): File {
    val moduleDir = File(System.getProperty("user.dir"))
    val candidate = moduleDir.resolve("src/test/fixtures/$fixtureName")
    require(candidate.isDirectory) {
        "Fixture directory not found at ${candidate.absolutePath}. " +
            "Expected it relative to module project dir: ${moduleDir.absolutePath}"
    }
    return candidate
}

/**
 * Returns the Android SDK directory from `ANDROID_HOME` or `ANDROID_SDK_ROOT`, or null when
 * neither is set or the path is not a directory. Used by integration tests that need an
 * Android SDK to run the Android Gradle plugin; without it they skip via JUnit `Assume`.
 */
internal fun androidSdkDirOrNull(): File? {
    val path =
        System.getenv("ANDROID_HOME")?.takeIf { it.isNotBlank() }
            ?: System.getenv("ANDROID_SDK_ROOT")?.takeIf { it.isNotBlank() }
            ?: return null
    return File(path).takeIf { it.isDirectory }
}
