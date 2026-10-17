package app.folio.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.folio.core.designsystem.Folio
import app.folio.core.designsystem.FolioType
import app.folio.core.designsystem.components.CircleButton
import app.folio.core.designsystem.components.FolioSheet
import app.folio.core.designsystem.components.GroupHeader
import app.folio.core.designsystem.components.GroupedCard
import app.folio.core.designsystem.components.GroupedRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Third-party code and fonts shipped in the APK. [groups] are the Maven groups an entry covers; a test checks
 * every library of `gradle/libs.versions.toml` against them, so a new dependency can't ship unlisted.
 * [text] is a file in `assets/licenses/`.
 */
internal data class OpenSource(val name: String, val holder: String, val license: String, val text: String, val groups: List<String> = emptyList())

internal object OpenSourceList {
    private const val APACHE = "Apache License 2.0"
    private const val OFL = "SIL Open Font License 1.1"

    val libraries = listOf(
        OpenSource("AndroidX, Jetpack Compose, Room, WorkManager", "The Android Open Source Project", APACHE, "apache-2.0.txt", listOf("androidx.")),
        OpenSource("Kotlin, kotlinx.coroutines, kotlinx.serialization", "JetBrains s.r.o.", APACHE, "apache-2.0.txt", listOf("org.jetbrains.kotlin")),
        OpenSource("Dagger Hilt", "The Dagger Authors", APACHE, "apache-2.0.txt", listOf("com.google.dagger")),
        OpenSource("Coil", "Coil Contributors", APACHE, "apache-2.0.txt", listOf("io.coil-kt")),
        OpenSource("Haze", "Chris Banes", APACHE, "apache-2.0.txt", listOf("dev.chrisbanes.haze")),
        OpenSource("Reorderable", "Calvin Liang", APACHE, "apache-2.0.txt", listOf("sh.calvin.reorderable")),
        OpenSource("jsoup", "Jonathan Hedley", "MIT License", "jsoup.txt", listOf("org.jsoup")),
    )

    val fonts = listOf(
        OpenSource("Inter", "The Inter Project Authors", OFL, "inter.txt"),
        OpenSource("Literata", "The Literata Project Authors", OFL, "literata.txt"),
        OpenSource("Lora", "The Lora Project Authors", OFL, "lora.txt"),
        OpenSource("PT Serif", "ParaType Ltd.", OFL, "pt-serif.txt"),
        OpenSource("Source Serif", "The Source Serif Project Authors", OFL, "source-serif.txt"),
    )
}

/** «Лицензии» from «О приложении»: the list, and a license's full text in the same sheet. */
@Composable
internal fun LicensesSheet(onDismiss: () -> Unit) {
    val c = Folio.colors
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = (OpenSourceList.libraries + OpenSourceList.fonts).firstOrNull { it.name == open }
    FolioSheet(onDismiss = onDismiss) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            if (shown != null) {
                CircleButton(Icons.AutoMirrored.Rounded.ArrowBackIos, stringResource(R.string.settings_back), { open = null }, modifier = Modifier.align(Alignment.CenterStart))
            }
            Text(shown?.name ?: stringResource(R.string.settings_licenses), style = FolioType.headline, color = c.label, modifier = Modifier.align(Alignment.Center).padding(horizontal = 56.dp), maxLines = 1)
            CircleButton(Icons.Rounded.Close, stringResource(R.string.settings_close), onDismiss, modifier = Modifier.align(Alignment.CenterEnd))
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            if (shown != null) {
                LicenseText(shown.text)
            } else {
                Group(stringResource(R.string.settings_licenses_libraries), OpenSourceList.libraries) { open = it.name }
                Group(stringResource(R.string.settings_licenses_fonts), OpenSourceList.fonts) { open = it.name }
            }
        }
    }
}

@Composable
private fun Group(title: String, items: List<OpenSource>, onOpen: (OpenSource) -> Unit) {
    GroupHeader(title)
    GroupedCard {
        items.forEachIndexed { i, item ->
            GroupedRow(item.name, subtitle = "${item.holder} · ${item.license}", chevron = true, showDivider = i < items.lastIndex, onClick = { onOpen(item) })
        }
    }
}

@Composable
private fun LicenseText(file: String) {
    val context = LocalContext.current
    val text by produceState("", file) {
        value = withContext(Dispatchers.IO) { runCatching { context.assets.open("licenses/$file").bufferedReader().use { it.readText() } }.getOrDefault("") }
    }
    Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp, color = Folio.colors.label, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
}
