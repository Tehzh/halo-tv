package app.orchard.tv.ui.home

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WallpaperStopObserverTest {
    private class Owner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this)
    }
    @Test fun releasesOnStopWithoutAnotherUiFrameAndNotOnPause() {
        val owner = Owner()
        var releases = 0
        var changes = 0
        owner.lifecycle.addObserver(WallpaperStopObserver({ releases++; true }, { changes++ }))
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        assertEquals(0, releases)
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        assertEquals(1, releases)
        assertEquals(1, changes)
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        assertEquals(1, releases)
    }
    @Test fun emptyPlayerDoesNotRequestARestart() {
        val owner = Owner()
        var changes = 0
        owner.lifecycle.addObserver(WallpaperStopObserver({ false }, { changes++ }))
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        assertEquals(0, changes)
    }
}
