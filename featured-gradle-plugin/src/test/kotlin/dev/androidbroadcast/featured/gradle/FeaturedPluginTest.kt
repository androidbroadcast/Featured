package dev.androidbroadcast.featured.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FeaturedPluginTest {

    @Test
    fun `plugin registers under correct id`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")
        assertTrue(project.plugins.hasPlugin("dev.androidbroadcast.featured"))
    }

    @Test
    fun `plugin applies without error`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")
        assertNotNull(project.plugins.getPlugin(FeaturedPlugin::class.java))
    }
}
