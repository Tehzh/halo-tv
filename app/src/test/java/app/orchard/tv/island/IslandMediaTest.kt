package app.orchard.tv.island

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Looper
import android.provider.Settings
import app.orchard.tv.guard.RevivalListener
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class IslandMediaTest {
    private val app: Context = RuntimeEnvironment.getApplication()
    private val listener = ComponentName(app, RevivalListener::class.java)
    private val setting = "enabled_notification_listeners"

    private fun setAccess(allowed: Boolean) {
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(manager).setNotificationListenerAccessGranted(listener, allowed)
        Settings.Secure.putString(app.contentResolver, setting, if (allowed) listener.flattenToString() else "")
        app.contentResolver.notifyChange(Settings.Secure.getUriFor(setting), null)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun onlySelectedPlaybackIsObservedAndRevocationDetachesCallbacks() = runBlocking {
        val manager = app.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val registry = shadowOf(manager)
        val chosenSession = MediaSession(app, "selected")
        val otherSession = MediaSession(app, "other")
        val chosen = MediaController(app, chosenSession.sessionToken)
        val other = MediaController(app, otherSession.sessionToken)
        val chosenShadow = shadowOf(chosen)
        val otherShadow = shadowOf(other)
        val playing = PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).build()
        val paused = PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED, 0, 0f).build()
        val cover = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        val song = MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Song one")
            .putBitmap(MediaMetadata.METADATA_KEY_ART, cover).build()
        chosenShadow.setPackageName("music.selected")
        chosenShadow.setPlaybackState(playing)
        chosenShadow.setMetadata(song)
        otherShadow.setPackageName("music.other")
        otherShadow.setPlaybackState(playing)
        otherShadow.setMetadata(song)
        registry.addController(other)
        registry.addController(chosen)
        Settings.Secure.putString(app.contentResolver, setting, "other.app/other.app.Listener")
        assertFalse(islandNotificationAccess(app))
        var track: IslandTrack? = null
        val observe = launch(start = CoroutineStart.UNDISPATCHED) { islandTracks(app, "music.selected").collect { track = it } }
        try {
            yield()
            assertTrue(chosenShadow.callbacks.isEmpty())

            setAccess(true)
            repeat(3) { yield() }
            assertTrue(islandNotificationAccess(app))
            assertEquals(1, chosenShadow.callbacks.size)
            assertTrue(otherShadow.callbacks.isEmpty())
            assertEquals("Song one", track?.title)
            checkNotNull(checkNotNull(track).art(app, "music.selected")).let {
                assertEquals(64, it.width)
                assertEquals(32, it.height)
            }

            val next = MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Song two").build()
            chosenShadow.setMetadata(next)
            chosenShadow.executeOnMetadataChanged(next)
            shadowOf(Looper.getMainLooper()).idle()
            repeat(3) { yield() }
            assertEquals("Song two", track?.title)

            chosenShadow.executeOnPlaybackStateChanged(paused)
            shadowOf(Looper.getMainLooper()).idle()
            repeat(3) { yield() }
            assertNull(track)
            chosenShadow.executeOnPlaybackStateChanged(playing)
            shadowOf(Looper.getMainLooper()).idle()
            repeat(3) { yield() }
            assertEquals("Song two", track?.title)

            setAccess(false)
            repeat(3) { yield() }
            assertNull(track)
            assertTrue(chosenShadow.callbacks.isEmpty())

            setAccess(true)
            repeat(3) { yield() }
        } finally {
            observe.cancelAndJoin()
            chosenSession.release()
            otherSession.release()
        }
        assertTrue(chosenShadow.callbacks.isEmpty())
    }

    @Test fun islandIsOffByDefaultAndSourceChoicesPersistIndependently() {
        val preferences = IslandPreferences(app)
        assertFalse(preferences.options.enabled)
        assertNull(preferences.options.musicPackage)
        preferences.change { it.copy(enabled = true, musicPackage = "selected.pkg", updates = false) }
        preferences.change { it.copy(enabled = false) }
        val reread = IslandPreferences(app).options
        assertFalse(reread.enabled)
        assertEquals("selected.pkg", reread.musicPackage)
        assertFalse(reread.updates)
        assertTrue(reread.transfers)
    }
}
