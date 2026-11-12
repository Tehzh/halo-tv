package app.orchard.tv.announcements

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AnnouncementsStateTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("halo_announcements", Context.MODE_PRIVATE)
    private fun startProcess() {
        ReflectionHelpers.setStaticField(Announcements::class.java, "prefs", null)
        ReflectionHelpers.getStaticField<MutableSet<String>>(Announcements::class.java, "presented").clear()
        Announcements.init(context, "guard", "3.0.1", 179)
    }
    private fun feed(kind: String = "important", revision: Int = 1) = """{"schema":1,"enabled":true,"announcements":[{"id":"test","revision":$revision,"kind":"$kind","title":"Notice","body":"Message"}]}"""
    @Before fun reset() { prefs.edit().clear().commit(); startProcess() }
    @Test fun regularNoticeHasAnEntryButNeverAnAutomaticPopup() {
        prefs.edit().putString("feed", feed("info")).commit(); startProcess()
        assertNotNull(Announcements.unread()); assertNull(Announcements.popup())
    }
    @Test fun acknowledgedRevisionStaysAcknowledgedAfterRestart() {
        prefs.edit().putString("feed", feed()).commit(); startProcess()
        Announcements.acknowledge(Announcements.popup()!!); startProcess()
        assertNull(Announcements.popup()); assertNull(Announcements.unread())
        assertNotNull(Announcements.latest())
    }
    @Test fun revisedNoticeCanAppearAfterTheOldRevisionWasRead() {
        prefs.edit().putString("feed", feed()).commit(); startProcess()
        Announcements.acknowledge(Announcements.popup()!!)
        prefs.edit().putString("feed", feed(revision = 2)).commit(); startProcess()
        assertEquals("test:2", Announcements.popup()?.key)
    }
    @Test fun snoozeSurvivesRestartAndKeepsManualReadingAvailable() {
        prefs.edit().putString("feed", feed()).commit(); startProcess()
        val n = Announcements.popup()!!; Announcements.later(n); startProcess()
        assertNull(Announcements.popup()); assertNotNull(Announcements.unread())
        assertTrue(prefs.getLong("later:${n.key}", 0) > System.currentTimeMillis())
    }
    @Test fun aNoticeAppearsAtMostOncePerProcessWithoutAResponse() {
        prefs.edit().putString("feed", feed()).commit(); startProcess()
        val n = Announcements.popup()!!; Announcements.presented(n)
        assertNull(Announcements.popup()); assertNotNull(Announcements.unread())
    }
    @Test fun snoozeCanExpireWithoutRestartingTheLauncher() {
        prefs.edit().putString("feed", feed()).commit(); startProcess()
        val n = Announcements.popup()!!; Announcements.presented(n); Announcements.later(n)
        assertNull(Announcements.popup())
        prefs.edit().putLong("later:${n.key}", System.currentTimeMillis() - 1).commit()
        assertEquals(n.key, Announcements.popup()?.key)
    }
    @Test fun corruptCachedFeedDoesNotBreakStartup() {
        prefs.edit().putString("feed", "{broken").commit(); startProcess()
        assertNull(Announcements.latest()); assertNull(Announcements.popup())
    }
    @Test fun emptyOrDisabledFeedHasNoManualEntry() {
        assertNull(Announcements.latest())
        Announcements.acceptFeed("", emptyList())
        assertNull(Announcements.unread())
        assertNull(Announcements.popup())
    }
    @Test fun expiredOrOtherVariantNoticesDoNotCreateAnEntry() {
        Announcements.acceptFeed("", listOf(
            RemoteAnnouncement("expired", 1, "Past", "Body", expiresAt = System.currentTimeMillis() - 1),
            RemoteAnnouncement("lite", 1, "Lite only", "Body", variants = setOf("lite")),
        ))
        assertNull(Announcements.latest())
    }
    @Test fun unchangedFeedDoesNotInvalidateTheDesktopButWithdrawalDoes() {
        var changes = 0
        val listener: () -> Unit = { changes++ }
        Announcements.observe(listener)
        try {
            Announcements.acceptFeed("", emptyList())
            assertEquals(0, changes)
            val list = AnnouncementFeed.parse(feed("info"))
            Announcements.acceptFeed(feed("info"), list)
            assertEquals(1, changes)
            Announcements.acceptFeed(feed("info"), list.toList())
            assertEquals(1, changes)
            Announcements.acknowledge(Announcements.latest()!!)
            assertNotNull(Announcements.latest())
            Announcements.acceptFeed("", emptyList())
            assertEquals(3, changes)
            assertNull(Announcements.latest())
        } finally { Announcements.removeObserver(listener) }
    }
}
