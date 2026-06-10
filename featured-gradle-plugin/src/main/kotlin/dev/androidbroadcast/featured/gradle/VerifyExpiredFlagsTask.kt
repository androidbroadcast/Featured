package dev.androidbroadcast.featured.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Gradle task that reads the [ResolveFlagsTask] output and emits a build-time warning for
 * every feature flag whose [LocalFlagEntry.expiresAt] date is in the past.
 *
 * Expired flags indicate dead code that should be cleaned up — the warning surfaces the
 * information at build time so engineers do not need a separate audit tool.
 *
 * The task is intentionally NOT annotated with [@CacheableTask].
 * [outputsUpToDateWhen] is set to `{ false }` so the check always runs even when
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

    init {
        outputs.upToDateWhen { false }
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
        for (entry in entries) {
            val raw = entry.expiresAt ?: continue
            try {
                val date = LocalDate.parse(raw)
                if (date < today) {
                    logger.warn("Featured: flag '${entry.key}' in ${entry.moduleName} expired on ${entry.expiresAt}")
                }
            } catch (_: DateTimeParseException) {
                logger.warn(
                    "Featured: flag '${entry.key}' in ${entry.moduleName} has invalid expiresAt" +
                        " format '$raw' (expected YYYY-MM-DD)",
                )
            }
        }
    }
}
