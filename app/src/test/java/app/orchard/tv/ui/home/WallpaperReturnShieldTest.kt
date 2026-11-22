package app.orchard.tv.ui.home

import android.app.Activity
import android.graphics.Bitmap
import android.os.Looper
import android.view.View
import android.widget.ImageView
import app.orchard.tv.ui.components.WallpaperReturnGate
import app.orchard.tv.ui.components.WallpaperReturnShield
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class WallpaperReturnShieldTest {
    @Test fun timeoutCannotRevealANewerReturnAndDoesNotWaitForMissingFrameForever() {
        val gate = WallpaperReturnGate()
        val old = gate.cover(); val current = gate.cover()
        assertFalse(gate.expire(old)); assertTrue(gate.covered)
        assertTrue(gate.expire(current)); assertFalse(gate.covered)
        assertFalse(gate.reveal(gate.rendered()))
    }
    @Test fun failedCaptureDoesNotReplaceTheLastGoodFallback() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val shield = WallpaperReturnShield(controller.get(), View(controller.get()))
        val good = Bitmap.createBitmap(90, 90, Bitmap.Config.ARGB_8888)
        for (y in 0..89) for (x in 0..89) good.setPixel(x, y, android.graphics.Color.rgb(x * 2, y * 2, 80))
        val black = Bitmap.createBitmap(90, 90, Bitmap.Config.ARGB_8888)
        black.eraseColor(android.graphics.Color.BLACK)
        shield.fallback(good); shield.fallback(black)
        assertSame(good, ReflectionHelpers.getField<Bitmap>(shield, "frame"))
        controller.pause().stop().destroy()
    }
    @Test fun visibleCoverExpiresWhenTheDecoderNeverSubmitsAFrame() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val shield = WallpaperReturnShield(controller.get(), View(controller.get()))
        controller.get().setContentView(shield)
        controller.visible()
        assertTrue("A visible attached window is required to start the deadline", shield.isAttachedToWindow)
        // Robolectric attaches the decor but does not deliver ViewRootImpl's visibility event.
        // Dispatch the actual native callback rather than setting the shield's private state.
        shield.dispatchWindowVisibilityChanged(View.VISIBLE)
        assertTrue(ReflectionHelpers.getField(shield, "windowVisible"))
        shield.cover()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1400))
        assertEquals(View.VISIBLE, shield.getChildAt(1).visibility)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        assertEquals(View.INVISIBLE, (shield.getChildAt(1) as ImageView).visibility)
        controller.pause().stop().destroy()
    }
    @Test fun legacyVideoKeepsItsPosterPastTheDeadlineUntilAFrameIsSubmitted() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val shield = WallpaperReturnShield(controller.get(), View(controller.get()), keepPosterUntilFirstFrame = true)
        controller.get().setContentView(shield)
        controller.visible()
        shield.dispatchWindowVisibilityChanged(View.VISIBLE)
        val poster = Bitmap.createBitmap(90, 90, Bitmap.Config.ARGB_8888)
        for (y in 0..89) for (x in 0..89) poster.setPixel(x, y, android.graphics.Color.rgb(x * 2, y * 2, 80))
        shield.fallback(poster)
        shield.cover()

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertEquals(View.VISIBLE, shield.getChildAt(1).visibility)
        val gate = ReflectionHelpers.getField<WallpaperReturnGate>(shield, "gate")
        assertTrue(gate.covered)
        assertFalse(gate.frameReady)

        shield.renderedFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        assertFalse(gate.covered)
        assertEquals(View.INVISIBLE, shield.getChildAt(1).visibility)
        controller.pause().stop().destroy()
    }

    @Test fun legacyVideoWithoutAUsablePosterCanStillExpireTheEmptyCover() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val shield = WallpaperReturnShield(controller.get(), View(controller.get()), keepPosterUntilFirstFrame = true)
        controller.get().setContentView(shield)
        controller.visible()
        shield.dispatchWindowVisibilityChanged(View.VISIBLE)
        shield.cover()

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(View.INVISIBLE, shield.getChildAt(1).visibility)
        controller.pause().stop().destroy()
    }

    @Test fun hiddenTimeDoesNotConsumeTheVisibleReturnDeadline() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val shield = WallpaperReturnShield(controller.get(), View(controller.get()))
        controller.get().setContentView(shield)
        controller.visible()
        shield.dispatchWindowVisibilityChanged(View.VISIBLE)
        shield.cover()
        shield.dispatchWindowVisibilityChanged(View.GONE)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertEquals(View.VISIBLE, shield.getChildAt(1).visibility)
        shield.dispatchWindowVisibilityChanged(View.VISIBLE)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1400))
        assertEquals(View.VISIBLE, shield.getChildAt(1).visibility)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        assertEquals(View.INVISIBLE, shield.getChildAt(1).visibility)
        controller.pause().stop().destroy()
    }
    @Test fun anotherReturnCancelsThePreviousDeadline() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val shield = WallpaperReturnShield(controller.get(), View(controller.get()))
        controller.get().setContentView(shield)
        controller.visible()
        shield.dispatchWindowVisibilityChanged(View.VISIBLE)
        shield.cover()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000))
        shield.cover()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        assertEquals(View.VISIBLE, shield.getChildAt(1).visibility)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000))
        assertEquals(View.INVISIBLE, shield.getChildAt(1).visibility)
        controller.pause().stop().destroy()
    }

    @Test fun coldLegacySurfaceWithNoPosterStaysCoveredThroughTheFirstBuffer() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val shield = WallpaperReturnShield(
            controller.get(), View(controller.get()), keepPosterUntilFirstFrame = true, protectInitialSurface = true,
        )
        controller.get().setContentView(shield)
        controller.visible()
        shield.dispatchWindowVisibilityChanged(View.VISIBLE)
        val picture = shield.getChildAt(1) as ImageView
        val clock = shadowOf(Looper.getMainLooper())
        val gate = ReflectionHelpers.getField<WallpaperReturnGate>(shield, "gate")
        clock.idleFor(Duration.ofSeconds(3))
        assertEquals(View.VISIBLE, picture.visibility)
        assertEquals(android.graphics.Color.BLACK, (picture.background as android.graphics.drawable.ColorDrawable).color)

        shield.renderedFrame()
        clock.idleFor(Duration.ofMillis(200))
        assertTrue(gate.covered)
        clock.idleFor(Duration.ofMillis(500))
        assertEquals(View.INVISIBLE, picture.visibility)
        assertFalse(shield.awaitingInitialFrame())

        shield.cover()
        clock.idleFor(Duration.ofSeconds(2))
        assertEquals(View.INVISIBLE, picture.visibility)
        controller.pause().stop().destroy()
    }

}
