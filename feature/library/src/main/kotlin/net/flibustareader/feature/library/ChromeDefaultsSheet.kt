package net.flibustareader.feature.library

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.flibustareader.core.data.settings.ReaderSettings
import net.flibustareader.core.data.settings.ChromeStyle
import net.flibustareader.core.designsystem.Flibusta
import net.flibustareader.core.designsystem.FlibustaType
import net.flibustareader.core.designsystem.ReaderPalettes
import net.flibustareader.core.designsystem.R as DesignR
import net.flibustareader.core.designsystem.components.ChromeStylePicker
import net.flibustareader.core.designsystem.components.CircleButton
import net.flibustareader.core.designsystem.components.CircleButtonStyle
import net.flibustareader.core.designsystem.components.FlibustaSheet
import net.flibustareader.core.designsystem.components.GroupHeader
import net.flibustareader.core.designsystem.components.SheetHeader

@Composable
internal fun ChromeDefaultsSheet(settings: ReaderSettings, onHeader: (ChromeStyle) -> Unit, onFooter: (ChromeStyle) -> Unit, onDone: () -> Unit) {
    val palette = ReaderPalettes.of(settings.theme, isSystemInDarkTheme())
    val styles = settings.chromeStyles
    FlibustaSheet(onDismiss = onDone, containerColor = Flibusta.colors.groupedBackground) {
        SheetHeader(stringResource(DesignR.string.chrome_defaults_title), trailing = {
            CircleButton(Icons.Rounded.Check, stringResource(R.string.chrome_done), onDone, style = CircleButtonStyle.Confirm)
        })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            GroupHeader(stringResource(DesignR.string.chrome_header_style))
            ChromeStylePicker(styles.header, true, palette, settings.style.font, onHeader)
            GroupHeader(stringResource(DesignR.string.chrome_footer_style))
            ChromeStylePicker(styles.footer, false, palette, settings.style.font, onFooter)
            Text(stringResource(DesignR.string.chrome_defaults_note), color = Flibusta.colors.secondaryLabel,
                style = FlibustaType.footnote, modifier = Modifier.padding(24.dp))
            Spacer(Modifier.height(32.dp))
        }
    }
}
