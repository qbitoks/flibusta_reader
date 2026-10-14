package app.folio.feature.reader

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Observe the device setting without changing its automatic brightness policy. */
@Composable
internal fun rememberDeviceBrightness(): Float {
    val resolver = LocalContext.current.contentResolver
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun read() = (Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f)
        .coerceIn(0.02f, 1f)
    var brightness by remember(resolver) { mutableFloatStateOf(read()) }
    DisposableEffect(resolver, lifecycle) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { brightness = read() }
        }
        resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, observer)
        val resume = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) brightness = read()
        }
        lifecycle.addObserver(resume)
        brightness = read()
        onDispose {
            resolver.unregisterContentObserver(observer)
            lifecycle.removeObserver(resume)
        }
    }
    return brightness
}
