package app.orchard.tv.guard

import android.content.Context
import android.media.AudioManager
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import app.orchard.tv.MainActivity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class VolumeRepeatRegressionTest {
    private val app = RuntimeEnvironment.getApplication()
    private val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val guard = Robolectric.buildService(HomeGuardService::class.java).get()
    private val home = Robolectric.buildActivity(MainActivity::class.java).get()
    private var down = 0L
    private val up = KeyEvent.KEYCODE_VOLUME_UP

    @Before fun reset() {
        app.getSharedPreferences("system", Context.MODE_PRIVATE).edit().clear().commit()
        ReflectionHelpers.setStaticField(HomeGuardService::class.java, "volumeLiftUnseen", false)
        ReflectionHelpers.setStaticField(HomeGuardService::class.java, "takenVolumeLiftSeen", false)
        ReflectionHelpers.setStaticField(HomeGuardService::class.java, "lastVolumeDown", Long.MIN_VALUE)
        ReflectionHelpers.getStaticField<MutableMap<Int, Long>>(HomeGuardService::class.java, "volumeUps").clear()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        down = SystemClock.uptimeMillis()
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, 5, 0)
    }
    private fun waitMs(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private fun level() = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
    private fun key(repeat: Int = 0, code: Int = up, action: Int = KeyEvent.ACTION_DOWN) =
        KeyEvent(down, SystemClock.uptimeMillis(), action, code, repeat)
    private fun filter(event: KeyEvent): Boolean = ReflectionHelpers.callInstanceMethod(
        guard, "takeVolumeKey", ClassParameter.from(KeyEvent::class.java, event))
    private fun window(event: KeyEvent): Boolean = ReflectionHelpers.callInstanceMethod(
        home, "dropVolumeKey", ClassParameter.from(KeyEvent::class.java, event))
    private fun consumedGuardDown(code: Int = up) {
        ReflectionHelpers.setField(guard, "volumeSeen", (if (code == up) 4 else 6) to false)
        assertTrue(filter(key(code = code)))
    }
    private fun consumedWindowDown() {
        ReflectionHelpers.setField(home, "volumeAtLift", 4 to false)
        assertTrue(window(key()))
    }

    @Test fun consumedGuardPressStillAdjustsOnActualHeldRepeats() {
        consumedGuardDown()
        waitMs(400); assertTrue(filter(key(1))); waitMs(300)
        assertEquals(6, level())
        waitMs(100); assertTrue(filter(key(2))); waitMs(300)
        assertEquals(7, level())
    }
    @Test fun firmwareHandledRepeatsAreNotDoubled() {
        consumedGuardDown()
        waitMs(400); audio.setStreamVolume(AudioManager.STREAM_MUSIC, 6, 0)
        assertTrue(filter(key(1))); waitMs(400)
        assertEquals(6, level())
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, 7, 0)
        assertTrue(filter(key(2))); waitMs(400)
        assertEquals(7, level())
    }
    @Test fun delayedFirmwareChangeCancelsTheFallback() {
        consumedGuardDown()
        waitMs(400); filter(key(1)); waitMs(100)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, 6, 0)
        waitMs(250)
        assertEquals(6, level())
    }
    @Test fun fastFirmwareRepeatsDoNotTurnPendingStepsIntoDoubleSteps() {
        consumedGuardDown()
        waitMs(400); filter(key(1)); waitMs(100)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, 6, 0)
        filter(key(2)); waitMs(400)
        assertEquals(6, level())
    }
    @Test fun fastUnmovedRepeatsAreNotAllCancelled() {
        consumedGuardDown()
        waitMs(400); filter(key(1)); waitMs(100); filter(key(2))
        waitMs(100); filter(key(3)); waitMs(300)
        assertEquals(8, level())
    }
    @Test fun loweringWorksAndMissingLiftDoesNotCreateAnEndlessTimer() {
        val lower = KeyEvent.KEYCODE_VOLUME_DOWN
        consumedGuardDown(lower)
        waitMs(400); filter(key(1, lower)); waitMs(10_000)
        assertEquals(4, level())
    }
    @Test fun guardAlwaysPassesLiftAndRejectsStaleRepeats() {
        consumedGuardDown()
        waitMs(400); filter(key(1)); waitMs(350)
        assertFalse(filter(key(action = KeyEvent.ACTION_UP)))
        assertTrue(filter(key(2))); waitMs(2000)
        assertEquals(6, level())
    }
    @Test fun ordinaryGuardVolumeKeyRemainsWithAndroid() {
        ReflectionHelpers.setField(guard, "volumeSeen", 5 to false)
        assertFalse(filter(key()))
        waitMs(400); assertFalse(filter(key(1))); waitMs(1000)
        assertEquals(5, level())
    }
    @Test fun unbindingCancelsAnyPendingVolumeChange() {
        consumedGuardDown()
        waitMs(400); filter(key(1))
        guard.onUnbind(android.content.Intent()); waitMs(1000)
        assertEquals(5, level())
    }
    @Test fun learnedSafeHeldTimerStillStopsOnLiftWithoutHardwareRepeats() {
        ReflectionHelpers.setStaticField(HomeGuardService::class.java, "takenVolumeLiftSeen", true)
        consumedGuardDown()
        waitMs(1000)
        assertTrue(level() > 5)
        assertFalse(filter(key(action = KeyEvent.ACTION_UP)))
        val stopped = level()
        waitMs(2000)
        assertEquals(stopped, level())
    }
    @Test fun unlearnedMissingLiftNeverStartsBlindRepeats() {
        consumedGuardDown()
        waitMs(10_000)
        assertEquals(5, level())
    }
    @Test fun repeatFromAnOlderPressCannotChangeVolumeAfterANewPress() {
        consumedGuardDown()
        waitMs(400)
        val stale = key(1)
        down = SystemClock.uptimeMillis()
        filter(key(code = KeyEvent.KEYCODE_VOLUME_DOWN))
        assertTrue(filter(stale)); waitMs(1000)
        assertEquals(5, level())
    }
    @Test fun windowConsumedPressAlsoKeepsHeldVolumeWorking() {
        consumedWindowDown()
        waitMs(400); assertTrue(window(key(1))); waitMs(300)
        assertEquals(6, level())
    }
    @Test fun windowDoesNotDoubleFirmwareStepsAndAlwaysPassesLift() {
        consumedWindowDown()
        waitMs(400); audio.setStreamVolume(AudioManager.STREAM_MUSIC, 6, 0)
        assertTrue(window(key(1))); waitMs(400)
        assertEquals(6, level())
        assertFalse(window(key(action = KeyEvent.ACTION_UP)))
        assertTrue(window(key(2))); waitMs(1000)
        assertEquals(6, level())
    }
    @Test fun windowKeepsExistingMissingLiftProtection() {
        ReflectionHelpers.setStaticField(HomeGuardService::class.java, "volumeLiftUnseen", true)
        consumedWindowDown()
        waitMs(400); assertTrue(window(key(1))); waitMs(1000)
        assertEquals(5, level())
    }
}
