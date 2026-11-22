package app.orchard.tv.guard

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], shadows = [QuizPlaybackTest.RecordingTransportControls::class])
class QuizPlaybackTest {
    private val context = RuntimeEnvironment.getApplication()
    private val sessions = mutableListOf<MediaSession>()

    @After fun release() = sessions.forEach(MediaSession::release)

    @Test fun pausesOnlyTheFrontPlayerAndResumesItAfterConfirmedPause() {
        val background = Player("music")
        val video = Player("video")
        val interruption = checkNotNull(QuizPlayback.pause(listOf(background.controller, video.controller), "video"))
        assertEquals(listOf("pause"), video.commands)
        assertEquals(emptyList<String>(), background.commands)

        // A pause request alone isn't evidence that the player actually paused.
        interruption.resume("video")
        assertEquals(listOf("pause"), video.commands)
        video.setState(PlaybackState.STATE_PAUSED)
        interruption.resume("video")
        assertEquals(listOf("pause", "play"), video.commands)
    }

    @Test fun ignoresAlreadyPausedUnsupportedAndAmbiguousSessions() {
        val video = Player("video")
        val second = Player("video")
        video.setState(PlaybackState.STATE_PAUSED)
        assertNull(QuizPlayback.pause(listOf(video.controller), "video"))
        video.setState(PlaybackState.STATE_PLAYING, 0L)
        assertNull(QuizPlayback.pause(listOf(video.controller), "video"))
        video.setState(PlaybackState.STATE_PLAYING)
        assertNull(QuizPlayback.pause(listOf(video.controller, second.controller), "video"))
        assertEquals(emptyList<String>(), video.commands)
        assertEquals(emptyList<String>(), second.commands)
    }

    @Test fun refusesToResumeIfForegroundMediaOrPlayCapabilityChanges() {
        val video = Player("video")
        val interruption = checkNotNull(QuizPlayback.pause(listOf(video.controller), "video"))
        video.setState(PlaybackState.STATE_PAUSED)
        interruption.resume("other")
        video.setState(PlaybackState.STATE_PAUSED, PlaybackState.ACTION_PAUSE)
        interruption.resume("video")
        video.setState(PlaybackState.STATE_PAUSED)
        video.setItem("new")
        interruption.resume("video")
        assertEquals(listOf("pause"), video.commands)
    }

    @Test fun usesTheExistingNotificationListenerPermission() {
        val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val sessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val listener = ComponentName(context, RevivalListener::class.java)
        val video = Player("video")
        shadowOf(sessionManager).addController(video.controller)

        assertNull(QuizPlayback.pause(context, "video"))
        shadowOf(notifications).setNotificationListenerAccessGranted(listener, true)
        checkNotNull(QuizPlayback.pause(context, "video"))
        assertEquals(listOf("pause"), video.commands)
    }

    @Implements(MediaController.TransportControls::class)
    class RecordingTransportControls {
        val commands = mutableListOf<String>()

        @Implementation fun pause() { commands += "pause" }
        @Implementation fun play() { commands += "play" }
    }

    private inner class Player(packageName: String) {
        private val session = MediaSession(context, packageName).also(sessions::add)
        val controller = MediaController(context, session.sessionToken)
        val commands: List<String> get() = Shadow.extract<RecordingTransportControls>(controller.transportControls).commands

        init {
            shadowOf(controller).setPackageName(packageName)
            setItem("original")
            setState(PlaybackState.STATE_PLAYING)
        }

        fun setItem(id: String) {
            shadowOf(controller).setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_MEDIA_ID, id).build())
        }

        fun setState(state: Int, actions: Long = PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY) {
            shadowOf(controller).setPlaybackState(PlaybackState.Builder().setState(state, 0, 1f).setActions(actions).build())
        }
    }
}
