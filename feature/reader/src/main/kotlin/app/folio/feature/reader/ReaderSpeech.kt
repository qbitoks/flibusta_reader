package app.folio.feature.reader

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.folio.core.designsystem.Folio
import app.folio.core.designsystem.FolioType
import app.folio.core.designsystem.ReaderPalette
import app.folio.core.designsystem.components.glass
import app.folio.core.layout.Mark
import app.folio.core.layout.MarkStyle
import app.folio.core.layout.TextSpan
import app.folio.core.speech.SpeechState

/** Speeds the rate chip cycles through. */
internal val SpeechRates = listOf(0.8f, 1f, 1.25f, 1.5f, 2f)

internal fun nextSpeechRate(rate: Float): Float = SpeechRates.firstOrNull { it > rate + 0.01f } ?: SpeechRates.first()

/** The phrase being spoken, as a soft band under the text; null when this book isn't read aloud. */
internal fun speechMark(state: SpeechState, bookId: String, palette: ReaderPalette, corner: Float): Mark? {
    val u = state.current?.takeIf { state.bookId == bookId } ?: return null
    val fill = palette.link.copy(alpha = if (palette.isDark) 0.3f else 0.16f).toArgb()
    return Mark(TextSpan(u.start, u.end), fill, MarkStyle.Highlight, corner)
}

/** «Слушать» asks for the notification permission first: the player lives in the notification. */
@Composable
internal fun rememberListen(vm: ReaderViewModel): () -> Unit {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.listen() }
    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) vm.listen()
        else permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

/** No voice for the book's language: say so and open the system TTS settings to install one. */
@Composable
internal fun NoVoiceEffect(vm: ReaderViewModel, state: SpeechState) {
    val context = LocalContext.current
    LaunchedEffect(state.noVoice) {
        if (!state.noVoice) return@LaunchedEffect
        Toast.makeText(context, R.string.speech_no_voice, Toast.LENGTH_LONG).show()
        try {
            context.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
        }
        vm.dismissNoVoice()
    }
}

/** Floating player over the page while this book is read aloud and the controls are hidden. */
@Composable
internal fun SpeechMiniPlayer(vm: ReaderViewModel, state: SpeechState, palette: ReaderPalette, modifier: Modifier = Modifier) {
    val c = Folio.colors
    Row(modifier.clip(RoundedCornerShape(28.dp)).glass(RoundedCornerShape(28.dp), tint = palette.background.copy(alpha = 0.92f))
        .readerPanelInput().padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        PlayerButton(Icons.Rounded.SkipPrevious, stringResource(R.string.speech_previous)) { vm.skipSpeech(-1) }
        PlayerButton(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            stringResource(if (state.playing) R.string.speech_pause else R.string.speech_play)) { vm.toggleSpeech() }
        PlayerButton(Icons.Rounded.SkipNext, stringResource(R.string.speech_next)) { vm.skipSpeech(1) }
        val rate = state.rate
        Box(Modifier.heightIn(min = 48.dp).widthIn(min = 56.dp).clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.speech_rate)) { vm.setSpeechRate(nextSpeechRate(rate)) },
            contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.speech_rate_value, formatRate(rate)), style = FolioType.subheadline, color = c.label)
        }
        PlayerButton(Icons.Rounded.Close, stringResource(R.string.speech_stop)) { vm.stopSpeech() }
    }
}

internal fun formatRate(rate: Float): String = java.text.NumberFormat.getInstance().apply { maximumFractionDigits = 2 }.format(rate)

@Composable
private fun PlayerButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = Folio.colors.label, modifier = Modifier.size(26.dp))
    }
}
