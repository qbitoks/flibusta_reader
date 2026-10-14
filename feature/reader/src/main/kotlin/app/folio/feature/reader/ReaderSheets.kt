package app.folio.feature.reader

import androidx.compose.runtime.Composable
import app.folio.core.designsystem.ReaderFont
import app.folio.core.designsystem.ReaderPalette
import app.folio.core.data.settings.ReaderSettings

@Composable
internal fun ReaderSheets(vm: ReaderViewModel, settings: ReaderSettings, palette: ReaderPalette, controls: ReaderControls) {
    when (controls.sheet) {
        ReaderSheet.Contents -> ContentsSheet(vm, palette, onDismiss = { controls.sheet = null })
        ReaderSheet.Bookmarks -> FragmentsSheet(vm, palette, ReaderFont.of(settings.themeStyle.font).composeFamily, onDismiss = { controls.sheet = null })
        ReaderSheet.Search -> SearchSheet(vm, palette, ReaderFont.of(settings.themeStyle.font).composeFamily, onDismiss = { controls.sheet = null })
        ReaderSheet.Themes -> ThemesSheet(
            vm,
            SheetBrightness(controls.brightness, controls.manualBrightness) {
                if (it >= 0f) controls.manualBrightness = it
                controls.brightness = it
            },
            onDismiss = { controls.sheet = null },
        )
        null -> Unit
    }
    controls.noteFor?.let { target ->
        NoteSheet(
            target = target,
            quote = vm.textOf(target.span),
            bookFont = ReaderFont.of(settings.themeStyle.font).composeFamily,
            palette = palette,
            onDone = { text ->
                if (target.highlightId != null) vm.setHighlightNote(target.highlightId, text)
                else vm.highlightWithNote(target.span, text)
                controls.noteFor = null
            },
        )
    }
    controls.note?.let { FootnoteSheet(it, palette, onDismiss = { controls.note = null }) }
    controls.image?.let { ImageViewer(it, onDismiss = { controls.image = null }) }
}
