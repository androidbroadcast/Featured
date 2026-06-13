package dev.androidbroadcast.featured.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Gradle task that reads the [ResolveFlagsTask] output and reports feature flags whose
 * [LocalFlagEntry.expiresAt] date is in the past.
 *
 * Expired flags indicate dead code that should be cleaned up. The reporting behaviour is
 * controlled by [mode]:
 * - [ExpiredFlagsMode.WARN] (default) — a Gradle warning is emitted per expired flag; the build
 *   continues normally.
 * - [ExpiredFlagsMode.ERROR] — the build fails with a [GradleException] that lists every expired
 *   flag (module · key · date) in a single message.
 *
 * Invalid `expiresAt` format strings are always reported as warnings and are **never** escalated
 * to an error, regardless of [mode]. They indicate a format problem, not an expiry event.
 *
 * The task is intentionally NOT annotated with [@CacheableTask].
 * [outputs.upToDateWhen] is set to `{ false }` so the check always runs even when
 * [ResolveFlagsTask] is restored FROM_CACHE.
 *
 * [flagsFile] declares the task-graph dependency on [ResolveFlagsTask] output. Caching is
 * explicitly disabled (`outputs.upToDateWhen { false }`), so this field does not contribute
 * to a cache key.
 *
 * A flag is reported as expired starting the day AFTER its `expiresAt` date — the flag is
 * considered valid through the expiry day itself.
 */
public abstract class VerifyExpiredFlagsTask : DefaultTask() {
    /**
     * Declared `@Internal`: the task is never up-to-date (`outputs.upToDateWhen { false }`),
     * so no input snapshot is taken; the task-graph dependency on [ResolveFlagsTask] is
     * established explicitly at registration via `dependsOn`.
     */
    @get:Internal
    public abstract val flagsFile: RegularFileProperty

    /**
     * Controls whether expired flags produce a warning ([ExpiredFlagsMode.WARN]) or fail
     * the build ([ExpiredFlagsMode.ERROR]). Wired from [FeaturedExtension.expiredFlagsMode]
     * at task registration time.
     *
     * Annotated `@Input` so that changing the mode from WARN to ERROR (or vice versa)
     * invalidates configuration-cache entries and the task re-runs on the next build.
     */
    @get:Input
    public abstract val mode: Property<ExpiredFlagsMode>

    init {
        outputs.upToDateWhen { false }
        mode.convention(ExpiredFlagsMode.WARN)
    }

    @TaskAction
    public fun verify() {
        check(flagsFile.isPresent) {
            "$path: flagsFile property is not set"
        }

        val file = flagsFile.get().asFile
        if (!file.exists()) {
            logger.warn(
                "Featured: verifyExpiredFlags — flags file not found at ${file.absolutePath}," +
                    " no expiry check performed",
            )
            return
        }

        val nonBlankLineCount = file.readLines().count { it.isNotBlank() }
        val entries = flagsFile.parseLocalFlagEntries()
        val dropped = nonBlankLineCount - entries.size
        if (dropped > 0) {
            logger.warn(
                "Featured: verifyExpiredFlags skipped $dropped unrecognized line(s) in ${file.absolutePath}",
            )
        }

        val today = LocalDate.now()
        val expiredMessages = mutableListOf<String>()

        for (entry in entries) {
            val raw = entry.expiresAt ?: continue
            try {
                val date = LocalDate.parse(raw)
                if (date < today) {
                    expiredMessages += "'${entry.key}' in ${entry.moduleName} expired on $raw"
                }
            } catch (_: DateTimeParseException) {
                // Invalid date format is a format diagnostic, not an expiry event — always warn,
                // never escalate to an error regardless of the configured mode.
                logger.warn(
                    "Featured: flag '${entry.key}' in ${entry.moduleName} has invalid expiresAt" +
                        " format '$raw' (expected YYYY-MM-DD)",
                )
            }
        }

        if (expiredMessages.isEmpty()) return

        when (mode.get()) {
            ExpiredFlagsMode.WARN -> {
                expiredMessages.forEach { message ->
                    logger.warn("Featured: flag $message")
                }
            }

            ExpiredFlagsMode.ERROR -> {
                throw GradleException(
                    buildString {
                        appendLine("Featured: the following flags have expired and must be cleaned up:")
                        expiredMessages.forEach { appendLine("  - $it") }
                    }.trimEnd(),
                )
            }
        }
    }
}
