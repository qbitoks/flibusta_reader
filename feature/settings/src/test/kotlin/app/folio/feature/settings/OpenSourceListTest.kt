package app.folio.feature.settings

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OpenSourceListTest {
    /** Build tools, tests and benchmarks: never shipped in the APK. */
    private val notShipped = listOf("junit:", "androidx.test", "androidx.benchmark", "androidx.compose.ui:ui-tooling",
        "org.jetbrains.kotlinx:kotlinx-coroutines-test", "compiler")

    @Test fun everyShippedLibraryIsListed() {
        val catalog = File("../../gradle/libs.versions.toml").readText()
        val modules = Regex("""module = "([^"]+)"""").findAll(catalog).map { it.groupValues[1] }
            .filterNot { m -> notShipped.any { it in m } }.toList()
        assertTrue(modules.isNotEmpty())
        val groups = OpenSourceList.libraries.flatMap { it.groups }
        val missing = modules.filterNot { m -> groups.any { m.startsWith(it) } }
        assertTrue("Add to OpenSourceList: $missing", missing.isEmpty())
    }

    @Test fun everyLicenseTextIsBundled() {
        (OpenSourceList.libraries + OpenSourceList.fonts).forEach {
            assertTrue(it.text, File("src/main/assets/licenses/${it.text}").length() > 500)
        }
    }

    @Test fun everyBundledFontIsListed() {
        val fonts = File("../../core/designsystem/src/main/res/font").list().orEmpty().filter { it.endsWith(".xml") }.map { it.removeSuffix(".xml") }
        val listed = OpenSourceList.fonts.map { it.name.lowercase().replace(' ', '_') }
        assertTrue("Add to OpenSourceList.fonts: ${fonts - listed.toSet()}", fonts.all { it in listed })
    }
}
