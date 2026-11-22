package app.orchard.tv.guard

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.core.content.getSystemService

/** Keeps the exact session paused by the quiz, for at most one resume request. */
internal class QuizPlayback private constructor(
    private val controller: MediaController,
    private val packageName: String,
    private val mediaId: String?,
    private val queueItem: Long,
) {
    fun resume(foregroundPackage: String?) {
        if (foregroundPackage != packageName) return
        runCatching {
            val state = controller.playbackState ?: return
            if (state.state != PlaybackState.STATE_PAUSED || state.actions and PlayActions == 0L) return
            if (controller.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) != mediaId ||
                state.activeQueueItemId != queueItem) return
            controller.transportControls.play()
        }
    }

    companion object {
        private const val PauseActions = PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE
        private const val PlayActions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PLAY_PAUSE

        fun pause(context: Context, foregroundPackage: String): QuizPlayback? {
            if (foregroundPackage == context.packageName || !GuardHealth.listenerAllowed(context)) return null
            return runCatching {
                val manager = context.getSystemService<MediaSessionManager>() ?: return null
                val listener = ComponentName(context, RevivalListener::class.java)
                pause(manager.getActiveSessions(listener), foregroundPackage)
            }.getOrNull()
        }

        internal fun pause(controllers: List<MediaController>, foregroundPackage: String): QuizPlayback? {
            val candidates = controllers.mapNotNull { controller ->
                if (controller.packageName != foregroundPackage) return@mapNotNull null
                val state = controller.playbackState ?: return@mapNotNull null
                if (state.state == PlaybackState.STATE_PLAYING && state.actions and PauseActions != 0L) {
                    controller to state
                } else null
            }
            // If more than one player in the front app is active, we cannot identify the video.
            val (controller, state) = candidates.singleOrNull() ?: return null
            val paused = QuizPlayback(controller, foregroundPackage,
                controller.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID), state.activeQueueItemId)
            controller.transportControls.pause()
            return paused
        }
    }
}
