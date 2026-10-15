package app.folio.feature.reader

import app.folio.core.data.settings.ReaderSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.Assert.assertFalse
import org.junit.Test

class SettingsEditorTest {
    @Test fun unchangedSliderValueDoesNotScheduleAnotherSave() {
        val job = Job()
        val scope = CoroutineScope(job + Dispatchers.Unconfined)
        try {
            val editor = SettingsEditor(ReaderSettings(), false, scope, {})
            editor.edit { it.copy(fontSize = it.fontSize) }
            assertFalse(job.children.any())
        } finally {
            scope.cancel()
        }
    }
}
