package kr.personal.sealion.one.launcher.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class NowPlaying(
    val packageName: String,
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val playing: Boolean,
)

/**
 * Notification-listener access is the only way a third-party launcher can see
 * other apps' media sessions. Grant it under 설정 › 미디어 › 알림 접근 허용.
 */
class MediaListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        MediaHub.attach(this)
    }

    override fun onListenerDisconnected() {
        MediaHub.detach()
    }
}

object MediaHub {
    private val state = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = state
    private var manager: MediaSessionManager? = null
    private var controller: MediaController? = null
    private var preferredPackage: String? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list.orEmpty()) }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() {
            controller = null
            publish()
        }
    }

    fun attach(context: Context) {
        val m = context.getSystemService(MediaSessionManager::class.java) ?: return
        val component = ComponentName(context, MediaListenerService::class.java)
        manager = m
        runCatching {
            m.addOnActiveSessionsChangedListener(sessionsListener, component)
            pick(m.getActiveSessions(component))
        }
    }

    fun detach() {
        runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        controller?.unregisterCallback(controllerCallback)
        controller = null
        state.value = null
    }

    fun setPreferred(packageName: String?) {
        preferredPackage = packageName
    }

    private fun pick(sessions: List<MediaController>) {
        val chosen = sessions.firstOrNull { it.packageName == preferredPackage }
            ?: sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull()
        if (chosen?.sessionToken == controller?.sessionToken) return publish()
        controller?.unregisterCallback(controllerCallback)
        controller = chosen
        chosen?.registerCallback(controllerCallback)
        publish()
    }

    private fun publish() {
        val c = controller ?: return run { state.value = null }
        val meta = c.metadata
        state.value = NowPlaying(
            packageName = c.packageName,
            title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
            artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty(),
            art = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART),
            playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
        )
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() = controller?.transportControls?.skipToNext()
    fun previous() = controller?.transportControls?.skipToPrevious()
}
