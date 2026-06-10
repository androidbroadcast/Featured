package dev.androidbroadcast.featured.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
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
 */
public abstract class VerifyExpiredFlagsTask : DefaultTask() {
    /**
     * `@InputFile` declares the task-graph dependency on `ResolveFlagsTask` output. Caching is
     * explicitly disabled (`outputs.upToDateWhen { false }`), so this field does not contribute
     * to a cache key.
     */
    @get:InputFile
    public abstract val flagsFile: RegularFileProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    public fun verify() {
        val today = LocalDate.now()
        val entries = flagsFile.parseLocalFlagEntries()

        val expired = mutableListOf<LocalFlagEntry>()
        for (entry in entries) {
            val raw = entry.expiresAt ?: continue
            try {
                val date = LocalDate.parse(raw)
                if (date < today) {
                    expired += entry
                }
            } catch (_: DateTimeParseException) {
                logger.warn(
                    "Featured: flag '${entry.key}' in ${entry.moduleName} has invalid expiresAt" +
                        " format '$raw' (expected YYYY-MM-DD)",
                )
            }
        }

        for (entry in expired) {
            logger.warn("Featured: flag '${entry.key}' in ${entry.moduleName} expired on ${entry.expiresAt}")
        }
    }
}
