package dev.androidbroadcast.featured.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Gradle task that generates R8 `-checkdiscard` rules for every `discard(...)` target declared
 * on a local boolean flag (see [FlagSpec.discard]).
 *
 * The rules are the verification companion to the `-assumevalues` rules from
 * [GenerateProguardRulesTask]: where `-assumevalues` *forces* a flag to its default and lets R8
 * drop the disabled branch, `-checkdiscard` *asserts* the guarded classes are gone from the
 * optimized output and fails the build otherwise.
 *
 * For Android application modules the generated file is wired into every variant's R8 run; for
 * library modules it is exported as a consumer ProGuard file so the check runs in the consuming
 * app's R8 — wherever the matching `-assumevalues` rule applies.
 *
 * Inputs are read directly from the `featured { }` DSL (not the resolved-flags report) so that
 * the discard targets and the remote-flag guard participate in the task's `@Input` fingerprint.
 */
@CacheableTask
public abstract class GenerateCheckDiscardRulesTask : DefaultTask() {
    /**
     * Discard targets for the module's local flags, one `flagKey|classSpec` line per target.
     * Wired from [FlagContainer.discardDescriptors] by [FeaturedPlugin].
     */
    @get:Input
    public abstract val discardDescriptors: ListProperty<String>

    /**
     * Keys of any **remote** flags that declared `discard(...)`. Remote flag values are resolved
     * at runtime and never pinned at build time, so a discard target on a remote flag can never
     * be guaranteed — the task fails with a precise message instead of emitting a rule that R8
     * would always reject.
     */
    @get:Input
    public abstract val remoteFlagsWithDiscards: ListProperty<String>

    /** Generated rules file. Written to `build/featured/proguard-featured-checkdiscard.pro`. */
    @get:OutputFile
    public abstract val outputFile: RegularFileProperty

    @TaskAction
    public fun generate() {
        val remoteOffenders = remoteFlagsWithDiscards.get()
        if (remoteOffenders.isNotEmpty()) {
            throw GradleException(
                "discard(...) is only supported on local flags, but it was declared on remote " +
                    "flag(s): ${remoteOffenders.joinToString(", ")}. Remote flag values are resolved " +
                    "at runtime and are never pinned at build time, so R8 cannot guarantee the " +
                    "guarded code is removed. Move the flag into localFlags { } or drop the discard(...) call.",
            )
        }

        val entries = discardDescriptors.get().mapNotNull { it.toCheckDiscardEntry() }
        val rules = CheckDiscardRulesGenerator.generate(entries)

        val out = outputFile.get().asFile
        out.parentFile?.mkdirs()
        out.writeText(rules)

        if (rules.isBlank()) {
            logger.lifecycle("[featured] No discard(...) targets — proguard-featured-checkdiscard.pro is empty.")
        } else {
            logger.lifecycle("[featured] Generated -checkdiscard rules for ${entries.size} target(s) → ${out.path}")
        }
    }
}

/**
 * Parses a `flagKey|classSpec` descriptor produced by [FlagContainer.discardDescriptors].
 * Returns `null` when the line has no `|` separator or an empty key/spec.
 */
private fun String.toCheckDiscardEntry(): CheckDiscardEntry? {
    val key = substringBefore('|', missingDelimiterValue = "")
    val spec = substringAfter('|', missingDelimiterValue = "")
    if (key.isEmpty() || spec.isBlank()) return null
    return CheckDiscardEntry(flagKey = key, classSpec = spec)
}
