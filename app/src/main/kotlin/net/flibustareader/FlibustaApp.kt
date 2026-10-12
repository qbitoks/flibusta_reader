package net.flibustareader

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import net.flibustareader.core.data.library.FolderScanner
import net.flibustareader.core.data.settings.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FlibustaApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var scanner: FolderScanner
    @Inject lateinit var settings: SettingsRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Folder watching is scheduled lazily and off the startup path.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            if (settings.library.first().watchedFolder != null) scanner.schedule(this@FlibustaApp)
        }
    }
}
