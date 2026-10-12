package net.flibustareader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import net.flibustareader.core.data.library.LibraryRepository
import net.flibustareader.core.designsystem.FlibustaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var library: LibraryRepository

    /** Books to open, produced by "Open with"/"Share" intents and consumed by the navigation. */
    private val openRequests = Channel<String>(Channel.CONFLATED)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Keep three-button navigation transparent over the app's own background.
        window.isNavigationBarContrastEnforced = false
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            FlibustaTheme {
                FlibustaNavHost(openRequests = openRequests.receiveAsFlow())
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uris: List<Uri> = when (intent?.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        if (uris.isEmpty()) return
        lifecycleScope.launch {
            var last: String? = null
            for (uri in uris) runCatching { library.import(uri) }.onSuccess { last = it.bookId }
            // Opening a single shared book goes straight to reading, as in Apple Books.
            if (uris.size == 1) last?.let { openRequests.send(it) }
        }
    }
}
