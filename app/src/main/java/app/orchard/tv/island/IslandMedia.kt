package app.orchard.tv.island

import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import app.orchard.tv.guard.GuardHealth
import app.orchard.tv.guard.RevivalListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

private const val NotificationListeners = "enabled_notification_listeners"
private const val ArtPixels = 64

private fun listenerComponent(context: Context) = ComponentName(context, RevivalListener::class.java)

internal fun islandNotificationAccess(context: Context): Boolean = GuardHealth.listenerAllowed(context)

internal fun observeIslandAccess(context: Context, onChange: () -> Unit): () -> Unit {
    val resolver = context.contentResolver
    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = onChange()
    }
    val registered = runCatching {
        resolver.registerContentObserver(Settings.Secure.getUriFor(NotificationListeners), false, observer)
    }.isSuccess
    return { if (registered) runCatching { resolver.unregisterContentObserver(observer) } }
}

internal data class IslandTrack(
    val controller: MediaController,
    val title: String,
    val revision: Long,
)

private fun MediaMetadata.trackTitle(): String? =
    (getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf(String::isNotBlank)
        ?: getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.takeIf(String::isNotBlank))?.take(220)

/** Only the selected app's controllers are observed, and only while the island is active. */
internal fun islandTracks(context: Context, source: String) = callbackFlow<IslandTrack?> {
    val sessions = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
    if (sessions == null) {
        close()
        return@callbackFlow
    }
    val main = Handler(Looper.getMainLooper())
    val component = listenerComponent(context)
    val observed = LinkedHashMap<MediaSession.Token, IslandSession>()
    var subscribed = false
    var last: IslandTrack? = null

    fun publish() {
        val next = observed.values.firstNotNullOfOrNull { it.track() }
        if (next != last) {
            last = next
            trySend(next)
        }
    }

    fun replace(controllers: List<MediaController>) {
        val selected = controllers.filter { it.packageName == source }
        val previous = LinkedHashMap(observed)
        observed.clear()
        selected.forEach { controller ->
            val token = controller.sessionToken
            val session = previous.remove(token) ?: IslandSession(controller, ::publish) {
                observed.remove(token)?.dispose()
                publish()
            }.takeIf { it.start(main) }
            if (session != null) observed[token] = session
        }
        previous.values.forEach(IslandSession::dispose)
        publish()
    }

    val activeChanged = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        if (subscribed && islandNotificationAccess(context)) replace(controllers.orEmpty())
        else replace(emptyList())
    }

    fun detach() {
        if (subscribed) runCatching { sessions.removeOnActiveSessionsChangedListener(activeChanged) }
        subscribed = false
        replace(emptyList())
    }

    fun refresh() {
        if (!islandNotificationAccess(context)) {
            detach()
            return
        }
        runCatching {
            if (!subscribed) {
                sessions.addOnActiveSessionsChangedListener(activeChanged, component, main)
                subscribed = true
            }
            replace(sessions.getActiveSessions(component))
        }.onFailure { detach() }
    }

    val unobserve = observeIslandAccess(context, ::refresh)
    try {
        refresh()
        awaitClose()
    } finally {
        unobserve()
        detach()
    }
}

private class IslandSession(
    private val controller: MediaController,
    private val changed: () -> Unit,
    private val destroyed: () -> Unit,
) : MediaController.Callback() {
    private var active = false
    private var playing = false
    private var current: IslandTrack? = null
    private var revision = 0L

    fun start(handler: Handler): Boolean = runCatching {
        controller.registerCallback(this, handler)
        active = true
        playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
        if (playing) updateMetadata(controller.metadata)
    }.onFailure { dispose() }.isSuccess

    fun dispose() {
        if (active) runCatching { controller.unregisterCallback(this) }
        active = false
        current = null
    }

    fun track(): IslandTrack? = current

    private fun updateMetadata(metadata: MediaMetadata?) {
        val title = runCatching { metadata?.trackTitle() }.getOrNull()
        current = title?.let { IslandTrack(controller, it, ++revision) }
    }

    override fun onPlaybackStateChanged(state: PlaybackState?) {
        if (!active) return
        val next = state?.state == PlaybackState.STATE_PLAYING
        if (next == playing) return
        playing = next
        updateMetadata(if (playing) runCatching { controller.metadata }.getOrNull() else null)
        changed()
    }

    override fun onMetadataChanged(metadata: MediaMetadata?) {
        if (active && playing) {
            updateMetadata(metadata)
            changed()
        }
    }

    override fun onSessionDestroyed() {
        if (active) destroyed()
    }
}

internal fun IslandTrack.art(context: Context, source: String): Bitmap? {
    val cover = runCatching {
        val metadata = controller.metadata?.takeIf { it.trackTitle() == title }
        metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
    }.getOrNull()
    if (cover != null && !cover.isRecycled) {
        runCatching {
            val scale = minOf(1f, ArtPixels.toFloat() / maxOf(cover.width, cover.height))
            if (scale < 1f) cover.scale(
                (cover.width * scale).toInt().coerceAtLeast(1),
                (cover.height * scale).toInt().coerceAtLeast(1),
            ) else cover.copy(Bitmap.Config.ARGB_8888, false)
        }.getOrNull()?.let { return it }
    }
    return runCatching {
        val icon = context.packageManager.getApplicationIcon(source).mutate()
        createBitmap(ArtPixels, ArtPixels).also { bitmap ->
            icon.setBounds(0, 0, ArtPixels, ArtPixels)
            icon.draw(Canvas(bitmap))
        }
    }.getOrNull()
}

internal fun openIslandMusic(context: Context, source: String, controller: MediaController): Boolean {
    if (controller.packageName != source || !islandNotificationAccess(context)) return false
    if (runCatching { controller.playbackState?.state }.getOrNull() != PlaybackState.STATE_PLAYING) return false
    val session = runCatching { controller.sessionActivity }.getOrNull()
    return openIslandMusicAction(source, session?.creatorPackage, { session?.send(); session != null }) {
        val pm = context.packageManager
        listOfNotNull(pm.getLeanbackLaunchIntentForPackage(source), pm.getLaunchIntentForPackage(source))
            .any { intent -> runCatching { context.startActivity(intent) }.isSuccess }
    }
}

internal fun openIslandMusicAction(
    source: String,
    creatorPackage: String?,
    openSession: () -> Boolean,
    launchApp: () -> Boolean,
): Boolean {
    if (creatorPackage == source && runCatching(openSession).getOrDefault(false)) return true
    return runCatching(launchApp).getOrDefault(false)
}
