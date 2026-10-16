package app.folio.core.speech

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps reading aloud with the screen off: a media-playback foreground service with a media
 * notification, lock-screen and headset controls. Lives while [SpeechController] has a book.
 */
@AndroidEntryPoint
class SpeechService : Service() {
    @Inject lateinit var controller: SpeechController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var session: MediaSession
    private var started = false

    private val noisy = object : BroadcastReceiver() {
        // Headphones unplugged: pause instead of reading out loud to the room.
        override fun onReceive(context: Context, intent: Intent) = controller.pause()
    }

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.speech_channel), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) },
        )
        session = MediaSession(this, "FolioSpeech").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = this@SpeechService.controller.resume()
                override fun onPause() = this@SpeechService.controller.pause()
                override fun onSkipToNext() = this@SpeechService.controller.skip(1)
                override fun onSkipToPrevious() = this@SpeechService.controller.skip(-1)
                override fun onStop() = this@SpeechService.controller.stop()
            })
            isActive = true
        }
        registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), RECEIVER_NOT_EXPORTED)
        scope.launch {
            controller.state
                .map { Triple(it.bookId, it.playing, it.title) }
                .distinctUntilChanged()
                .collect { (bookId, playing, _) ->
                    if (bookId == null) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        updateSession(playing)
                        show(playing)
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService needs startForeground soon, even if the book already stopped.
        if (!started) show(controller.state.value.playing)
        when (intent?.action) {
            ACTION_TOGGLE -> controller.toggle()
            ACTION_NEXT -> controller.skip(1)
            ACTION_PREVIOUS -> controller.skip(-1)
            ACTION_STOP -> controller.stop()
        }
        if (controller.state.value.bookId == null) stopSelf()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        unregisterReceiver(noisy)
        session.release()
        super.onDestroy()
    }

    private fun updateSession(playing: Boolean) {
        val state = controller.state.value
        session.setMetadata(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, state.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, state.author)
            .build())
        session.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP)
            .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
            .build())
    }

    private fun show(playing: Boolean) {
        val state = controller.state.value
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_speech)
            .setContentTitle(state.title)
            .setContentText(state.author)
            .setContentIntent(open)
            .setDeleteIntent(action(ACTION_STOP))
            .setOngoing(playing)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(button(android.R.drawable.ic_media_previous, R.string.speech_previous, ACTION_PREVIOUS))
            .addAction(if (playing) button(android.R.drawable.ic_media_pause, R.string.speech_pause, ACTION_TOGGLE)
                else button(android.R.drawable.ic_media_play, R.string.speech_play, ACTION_TOGGLE))
            .addAction(button(android.R.drawable.ic_media_next, R.string.speech_next, ACTION_NEXT))
            .addAction(button(android.R.drawable.ic_menu_close_clear_cancel, R.string.speech_stop, ACTION_STOP))
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        started = true
    }

    private fun button(icon: Int, label: Int, action: String) =
        Notification.Action.Builder(Icon.createWithResource(this, icon), getString(label), action(action)).build()

    private fun action(action: String): PendingIntent =
        PendingIntent.getService(this, action.hashCode(), Intent(this, SpeechService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE)

    private companion object {
        const val CHANNEL = "speech"
        const val NOTIFICATION_ID = 41
        const val ACTION_TOGGLE = "app.folio.speech.TOGGLE"
        const val ACTION_NEXT = "app.folio.speech.NEXT"
        const val ACTION_PREVIOUS = "app.folio.speech.PREVIOUS"
        const val ACTION_STOP = "app.folio.speech.STOP"
    }
}
