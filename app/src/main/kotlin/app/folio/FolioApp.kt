package app.folio

import android.app.Application
import android.app.UiModeManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.folio.core.data.catalog.DownloadManager
import app.folio.core.data.library.FolderScanner
import app.folio.core.data.settings.Appearance
import app.folio.core.data.settings.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FolioApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var scanner: FolderScanner
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var downloads: DownloadManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Folder watching is scheduled lazily and off the startup path.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            if (settings.library.first().watchedFolder != null) scanner.schedule(this@FolioApp)
        }
        DownloadNotifier(this, downloads).start(scope)
        // The app's own appearance goes through the system's per-app night mode: it changes the
        // configuration, so the windows, system bars and isSystemInDarkTheme() all follow it.
        val uiModes = getSystemService(UiModeManager::class.java)
        scope.launch {
            settings.appAppearance.collect {
                uiModes.setApplicationNightMode(
                    when (it) {
                        Appearance.Auto -> UiModeManager.MODE_NIGHT_AUTO
                        Appearance.Light -> UiModeManager.MODE_NIGHT_NO
                        Appearance.Dark -> UiModeManager.MODE_NIGHT_YES
                    },
                )
            }
        }
    }
}
