package dev.androidbroadcast.featured.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

public class FeaturedPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        // Scaffold: plugin applies successfully.
        // Future: register dead-code-elimination tasks wired before minification.
    }
}
