@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.orchard.tv.ui.home

import app.orchard.tv.R

import android.graphics.Bitmap
import android.view.SurfaceView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.asAndroidBitmap
import app.orchard.tv.ui.components.WallpaperReturnShield
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import app.orchard.tv.ui.components.FilmSlot
import app.orchard.tv.settings.AerialSettings
import app.orchard.tv.system.DiagLog
import app.orchard.tv.ui.theme.Motion
import app.orchard.tv.wallpaper.AerialStream
import app.orchard.tv.wallpaper.ScreensaverVideos
import app.orchard.tv.wallpaper.Wallpapers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The owner's own videos behind Home (issues #410, #583), sent from the phone page as for the video
 * screensaver: silent, filling the screen, one after another or just the one chosen on a loop (#762). They move when the aerial wallpaper
 * would (LiveWallpaper): on a still shelf, or all the time when set to keep moving. Stopped while
 * Home shows plainly (Control Center, Personalize), the paused film stays; under a frosted panel,
 * after a quiet while, or let go into an app, the frame it stopped on becomes the wallpaper's
 * picture: read off the video plane where the TV allows it, else its key frame decoded in the app,
 * else the film's existing poster. No second hardware decoder is opened for a transition.
 */
/**
 * The owner's video wallpaper's player, for the screensaver of their videos that carries it on
 * instead of starting its own (HomeScreen, SaverCarry.Video): left and right step through the
 * wallpaper's own films, in its order; one video chosen starts over.
 */
class HomeVideo {
    internal var player: ExoPlayer? = null
    /** Whether this surface contains a real frame, including when its player is paused. */
    var frameReady by mutableStateOf(false); internal set

    fun skip(step: Int) {
        val p = player ?: return
        if (step < 0) p.seekToPreviousMediaItem() else p.seekToNextMediaItem()
        if (p.playbackState == Player.STATE_IDLE) p.prepare()
    }

    /** The film and the moment showing now, while a player has it. */
    fun spot(): Pair<String, Long>? = player?.let { p -> p.currentMediaItem?.mediaId?.let { it to p.currentPosition } }

    companion object {
        /** Home's video wallpaper, for a screensaver of the owner's videos that starts on its own. */
        var onHome: HomeVideo? = null

        /**
         * Where a screensaver of the owner's videos over their video wallpaper starts: where the
         * wallpaper's player is (Home's own screensaver, as it opens), else where it was let go
         * (the system's dream, Home paused first), else the picture's moment.
         */
        fun start(): Pair<String, Long>? = onHome?.spot() ?: Wallpapers.videoLeftSpot() ?: Wallpapers.videoSpot()
    }
}

/**
 * The frame a screensaver of the owner's videos over their video wallpaper is at, decoded in the
 * app every few seconds (VideoSaver), so Home comes back on it and the wallpaper goes on from there
 * rather than from where it was before the screensaver, as the aerials do (SaverFrames). By
 * software, off the decoder the screensaver's film holds. A film that cannot be decoded so (4K HEVC
 * past VIDEO_KEY_FRAME_MS, no software decoder) leaves Home as it was.
 */
internal object VideoSaverFrames {
    class Picture(val film: String, val atMs: Long, val prepared: Wallpapers.PreparedFrame)

    @Volatile var latest: Picture? = null

    suspend fun make(context: android.content.Context, film: String, atMs: Long): Picture? {
        val (frame, at) = keyFrameOfVideo(context, film, atMs, Wallpapers.screenW, Wallpapers.screenH) ?: return null
        return Picture(film, at, Wallpapers.prepareLiveFrame(frame))
    }

    /** The screensaver ends: its last picture becomes the wallpaper's, once. On the main thread. */
    fun apply() {
        val pick = latest ?: return
        latest = null
        if (Wallpapers.isVideo() && Wallpapers.applyLiveFrame(pick.prepared, pick.film, pick.atMs)) {
            DiagLog.i("video wallpaper: back from the screensaver onto its picture (${pick.film} at ${pick.atMs} ms)")
        }
    }
}

@Composable
fun VideoWallpaper(
    moving: Boolean,
    retainPaused: Boolean = false,
    /** False once the screensaver is up or Home is behind an app: the decoder goes at once. */
    keep: Boolean,
    /** The screensaver carries the film on: it moves whatever the keys and the idle wait say. */
    hold: Boolean,
    home: HomeVideo,
    /** Nothing over Home that blurs it: the stopped film may stand in for its picture (as LiveWallpaper). */
    standIn: Boolean,
    lastInputAt: Long,
    cover: Animatable<Float, *>,
    /** Home has gone behind an app (not a screensaver): the frame may hold the decoder a moment (leaveOnPicture). */
    behindApp: Boolean = false,
) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var surface by remember { mutableStateOf<SurfaceView?>(null) }
    var showing by remember { mutableStateOf(false) }
    var returnShield by remember { mutableStateOf<WallpaperReturnShield?>(null) }
    LaunchedEffect(Wallpapers.backdrop, returnShield) {
        returnShield?.fallback(Wallpapers.backdrop?.asAndroidBitmap())
    }
    // The film's width over its height as shown (turned upright), 0 until its first frame says.
    var shape by remember { mutableStateOf(Wallpapers.videoSpot()?.first?.let { filmShapes[it] }) }
    var idle by remember { mutableStateOf(false) }
    var restart by remember { mutableIntStateOf(0) }
    var recoveryAttempts by remember { mutableIntStateOf(0) }
    var lastFailureNotice by remember { mutableStateOf(0L) }
    var playbackRequestedAt by remember { mutableStateOf(0L) }
    LaunchedEffect(lastInputAt, moving) {
        idle = false
        if (!moving) return@LaunchedEffect
        delay(START_AFTER_MS)
        idle = true
    }
    val play = moving && (idle || AerialSettings.wallpaperAlwaysMoves) || hold
    val keepNow by rememberUpdatedState(keep)
    val behindAppNow by rememberUpdatedState(behindApp)
    val standInNow by rememberUpdatedState(standIn)
    val playNow by rememberUpdatedState(play)
    val lastInputNow by rememberUpdatedState(lastInputAt)
    val scope = rememberCoroutineScope()
    val frames = remember { WallpaperFrameEpoch() }

    fun coverReturn() {
        returnShield?.fallback(Wallpapers.backdrop?.asAndroidBitmap())
        returnShield?.cover()
    }

    // Keep only playlist/player bookkeeping while another app owns the screen. stop() releases
    // decoder and buffers; it does NOT render or decode in background. Reusing the playlist avoids
    // file enumeration, player construction and discarded buffer setup on every return.
    fun parkForApp() {
        val p = player ?: return
        coverReturn()
        p.currentMediaItem?.mediaId?.let { Wallpapers.leaveVideoFilm(it, p.currentPosition) }
        frames.invalidate()
        home.frameReady = false
        p.pause()
        p.stop()
        showing = false
        Wallpapers.freezeFilmFrost()
    }

    fun release() {
        coverReturn()
        player?.let { p -> p.currentMediaItem?.mediaId?.let { Wallpapers.leaveVideoFilm(it, p.currentPosition) } }
        frames.invalidate()
        home.frameReady = false
        val oldPlayer = player
        player = null
        home.player = null
        oldPlayer?.release()
        showing = false
        Wallpapers.freezeFilmFrost()
    }

    fun stagePoster(id: String) {
        val token = frames.current()
        scope.launch {
            val poster = withContext(Dispatchers.IO) {
                Wallpapers.videoPoster(id, Wallpapers.screenW, Wallpapers.screenH)
            } ?: return@launch
            if (frames.accepts(token) && keepNow &&
                player?.currentMediaItem?.mediaId == id && Wallpapers.isVideo()) {
                // Under the video, ready for a pause/return on TVs that cannot capture the plane.
                Wallpapers.showLiveFrame(poster)
            }
        }
    }

    suspend fun open(): ExoPlayer? {
        val films = withContext(Dispatchers.IO) { Wallpapers.videoFilms() }
        if (films.isEmpty()) return null
        // Where the picture on screen came from, so moving again does not cut to another place.
        val spot = Wallpapers.videoSpot()
        val first = films.indexOfFirst { it.id == spot?.first }
        var rendered = false
        // The first film's shape, where it has played before: the view and the scaling are right
        // from its first frame, not after it (TCL MT5879 showed a turned film squeezed for a moment).
        if (first >= 0) filmShapes[films[first].id]?.let { shape = it }
        // Local files need little read-ahead. Do not retain the phone-sized default video buffer.
        val load = DefaultLoadControl.Builder().setTargetBufferBytes(24 * 1024 * 1024)
            .setBufferDurationsMs(2_000, 8_000, 0, 250).build()
        return ExoPlayer.Builder(context).setLoadControl(load).build().apply {
            home.player = this
            volume = if (AerialSettings.videoWallpaperMuted) 0f else 1f
            repeatMode = Player.REPEAT_MODE_ALL
            // Filled to the edges whatever its shape: a wallpaper has no bars.
            videoScalingMode = scalingFor(shape, AerialSettings.videoWallpaperFit)
            // Home is drawn over it: the display keeps its rate (see LiveWallpaper).
            videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
            addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    if (player !== this@apply) return
                    home.frameReady = true
                    if (playbackRequestedAt > 0L) {
                        DiagLog.i("video wallpaper first frame in ${android.os.SystemClock.elapsedRealtime() - playbackRequestedAt} ms")
                        playbackRequestedAt = 0L
                    }
                    rendered = true
                    showing = isPlaying
                    returnShield?.renderedFrame()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (player === this@apply) showing = isPlaying && rendered
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (player !== this@apply) return
                    if (videoSize.width <= 0 || videoSize.height <= 0) return
                    val turned = (videoFormat?.rotationDegrees ?: 0) % 180 != 0
                    val now = FilmShape(videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height, turned)
                    if (now != shape) DiagLog.i("video wallpaper film ${videoSize.width}x${videoSize.height}${if (turned) ", turned" else ""}")
                    shape = now
                    currentMediaItem?.mediaId?.let { filmShapes[it] = now }
                    videoScalingMode = scalingFor(now, AerialSettings.videoWallpaperFit)
                }

                // Playing them in turn where the glass cannot be read off the video: the picture under
                // it stayed the first film's, and so did the glass of the plate and Control Center
                // through every film after it (TCL MT9655, issue #1158). Each film's poster now.
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    if (home.player !== this@apply) return
                    val id = mediaItem?.mediaId ?: return
                    frames.begin(id)
                    if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                        rendered = false; home.frameReady = false; showing = false
                    }
                    if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED || Wallpapers.backdrop == null) stagePoster(id)
                    shape = filmShapes[id]
                    videoScalingMode = scalingFor(shape, AerialSettings.videoWallpaperFit)
                    if (copyGlassPossible || reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) return
                    val token = frames.current()
                    scope.launch {
                        val poster = withContext(Dispatchers.IO) { Wallpapers.videoPoster(id, Wallpapers.screenW, Wallpapers.screenH) } ?: return@launch
                        if (!frames.accepts(token) || !keepNow || player?.currentMediaItem?.mediaId != id || !Wallpapers.isVideo()) return@launch
                        val applied = Wallpapers.useLiveFrame(poster, id, 0L) { frames.accepts(token) && keepNow && player?.currentMediaItem?.mediaId == id }
                        // And the plate's glass while it moves, made once from the first film's picture
                        // (sampleGlass): it kept that film's colours until Control Center was opened
                        // (issue #1158 on 1.81, the reporter on MT9655).
                        if (applied) Wallpapers.useStillFilmFrost()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (player !== this@apply) return
                    home.frameReady = false
                    showing = false
                    DiagLog.i("video wallpaper failed (${error.errorCodeName})")
                    // Behind an app or a screensaver the decoder is theirs: no retry.
                    if (!keepNow) return
                    scope.launch { cover.snapTo(0f) }
                    val nowMs = android.os.SystemClock.elapsedRealtime()
                    if (lastFailureNotice == 0L || nowMs - lastFailureNotice >= 30_000L) {
                        lastFailureNotice = nowMs
                        val message = when (error.errorCode) {
                            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> R.string.halo_video_decoder_error
                            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> R.string.halo_video_damaged_error
                            else -> R.string.halo_video_playback_error
                        }
                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                    }
                    // One film this TV cannot decode; the others may still play.
                    if (mediaItemCount > 1) {
                        removeMediaItem(currentMediaItemIndex)
                        prepare()
                    } else {
                        val failed = this@apply
                        scope.launch {
                            delay(800)
                            if (keepNow && playNow && player === failed && recoveryAttempts < 2) {
                                recoveryAttempts++
                                release()
                                restart++
                            }
                        }
                    }
                }
            })
            setMediaItems(
                films.map { MediaItem.Builder().setUri(it.file.toUri()).setMediaId(it.id).build() },
                first.coerceAtLeast(0),
                if (first >= 0) spot?.second ?: 0L else 0L,
            )
            surface?.let { setVideoSurfaceView(it) }
            prepare()
        }
    }

    LaunchedEffect(player, shape, AerialSettings.videoWallpaperMuted, AerialSettings.videoWallpaperFit) {
        player?.volume = if (AerialSettings.videoWallpaperMuted) 0f else 1f
        player?.videoScalingMode = scalingFor(shape, AerialSettings.videoWallpaperFit)
    }

    /**
     * The frame [film] stopped on near [at], at the screen's size, and its moment: copied off the
     * video plane where PixelCopy reads it, else the key frame nearest decoded in the app, which
     * leaves the hardware decoder to the player. No MediaMetadataRetriever: it can take the next
     * app's hardware decoder or jam TCL's media server. Use the poster last, so the picture is the
     * film that played (issue #1158).
     */
    suspend fun frameOf(film: String, at: Long, held: Boolean): Pair<Bitmap, Long>? {
        val w = surface?.width?.takeIf { it > 0 } ?: Wallpapers.screenW
        val h = surface?.height?.takeIf { it > 0 } ?: Wallpapers.screenH
        // The copy is the whole film squeezed to the view's size: only where that is the screen's shape.
        if (held && screenShaped(shape?.shape ?: 0f)) surface?.let { copyFrame(it) }?.let { return it to at }
        keyFrameOfVideo(context, film, at, w, h)?.let { return it }
        return withContext(Dispatchers.IO) {
            Wallpapers.videoPoster(film, w, h)?.let { it to at }
        }
    }

    // Software-only catch-up behind the other app. Returning Home cancels it without waiting.
    var catchUp by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    /**
     * Lets the player go with Home out of sight, and the picture catches up behind it to where the
     * film was: stopped and left standing in (Control Center, then an app from it), no frame had
     * been taken, and Home came back on an older picture with the film starting over from its spot.
     */
    fun letGo(reuseForApp: Boolean = false) {
        val p = player ?: return
        // The leave effect already parked it; onStop must not start a second software frame extraction
        // while the other app starts. The first capture keeps its epoch and may finish offscreen.
        if (reuseForApp && p.playbackState == Player.STATE_IDLE) {
            coverReturn()
            home.frameReady = false
            return
        }
        val film = p.currentMediaItem?.mediaId
        val at = p.currentPosition
        if (reuseForApp) parkForApp() else release()
        if (film == null) return
        catchUp?.cancel()
        val token = frames.begin(film)
        catchUp = scope.launch {
            frameOf(film, at, held = false)?.let { (frame, _) ->
                Wallpapers.useLiveFrame(frame, film, at) {
                    frames.accepts(token) && !keepNow && (player == null || player?.playbackState == Player.STATE_IDLE) && Wallpapers.videoLeftSpot() == (film to at)
                }
            }
        }
    }

    /**
     * Into an app: stopped on its frame, that frame becomes the picture before the video goes, as
     * the aerial film does (LiveWallpaper.leaveOnPicture). Let go at once, the picture under it was
     * one from an earlier stop, which showed through the app's opening as a flash, and came back
     * on return before this moment's caught up (Xiaomi MiTV-MFFU0, Android 14, one of the owner's
     * videos; issue #891, the reporter's log: the frame of where it was came 0.4-0.6 s after the
     * pause, behind the app). False when no frame came within LEAVE_FRAME_MS: let go as before.
     */
    suspend fun leaveOnPicture(p: ExoPlayer): Boolean {
        p.pause()
        val film = p.currentMediaItem?.mediaId ?: return false
        val at = p.currentPosition
        val started = android.os.SystemClock.elapsedRealtime()
        val epoch = frames.current()
        // Keep Halo's short decoder handoff budget. Never hold another app's decoder for a
        // retriever/software read; a slower capture catches up after release in letGo().
        val frame = surface?.takeIf { screenShaped(shape?.shape ?: 0f) }
            ?.let { withTimeoutOrNull(350) { copyFrame(it) } } ?: return false
        if (player !== p || !frames.accepts(epoch) || keepNow) { frame.recycle(); return false }
        Wallpapers.showLiveFrame(frame)
        cover.snapTo(0f)
        if (behindAppNow) parkForApp() else release()
        Wallpapers.leaveVideoFilm(film, at)
        DiagLog.i("video wallpaper left on its frame in ${android.os.SystemClock.elapsedRealtime() - started} ms")
        catchUp?.cancel()
        val token = frames.begin(film)
        catchUp = scope.launch {
            Wallpapers.useLiveFrame(frame, film, at) {
                frames.accepts(token) && !keepNow && (player == null || player?.playbackState == Player.STATE_IDLE) && Wallpapers.videoLeftSpot() == (film to at)
            }
        }
        return true
    }

    // The plate's glass over the moving video, where PixelCopy reads it. Not from the file: an
    // owner's video may be 4K HEVC, and a second decoder beside the player's may not be there.
    LaunchedEffect(play, showing) {
        if (!play || !showing) return@LaunchedEffect
        // Once per playback start for an older upload without a poster. This reads the existing
        // player surface, not the file, and never opens another decoder on TCL televisions.
        val id = player?.currentMediaItem?.mediaId
        if (id != null && !withContext(Dispatchers.IO) { Wallpapers.hasVideoPoster(id) }) {
            delay(1000)
            if (player?.currentMediaItem?.mediaId == id) {
                surface?.let { copyVideoPoster(it) }?.let { Wallpapers.keepPlayingVideoPoster(id, it) }
            }
        }
        // Keep the previous glass through the short page/decoder transition.
        delay(1_200)
        sampleGlass({ player }, canTake = { copyGlassPossible }) {
            surface?.let { copyGlass(it) }
        }
    }

    // Let go as Aerial stops, not at the next frame, which does not come then (rememberStops).
    val stops = rememberStops {
        if (player == null) return@rememberStops false
        // Release synchronously; a stopped Activity may never run another Compose frame.
        // The still background remains visible until the returning player's first frame.
        player?.pause()
        letGo(reuseForApp = !AerialStart.running)
        scope.launch { cover.snapTo(0f) }
        true
    }

    LaunchedEffect(play, stops, keep, restart, retainPaused) {
        // Losing Home or entering a screensaver must cancel an in-flight frame capture and free
        // the decoder even if playback was already paused. Several TVs have only one video path.
        if (!keep) {
            recoveryAttempts = 0
            player?.pause()
            val current = player
            if (current != null && current.playbackState != Player.STATE_IDLE &&
                !leaveOnPicture(current) && current === player) letGo(reuseForApp = behindAppNow)
            cover.snapTo(0f)
            return@LaunchedEffect
        }
        if (play) {
            // Catch-up uses only a cancellable software decoder. Do not put its old frame or IO
            // ahead of the new hardware player's first frame on the visible return path.
            catchUp?.cancel()
            catchUp = null
            // Back from a screensaver of these videos: its last picture first, so the film opens
            // there. The dream's window went after Home was back, and a film set to keep moving had
            // opened at its spot from before (TCL MT5879: 17 s into the film, the dream at 38 s).
            if (VideoSaverFrames.latest != null) {
                // A separate system dream may have advanced another video while Home was stopped.
                // Do not resume the parked playlist at its pre-dream position.
                release()
                VideoSaverFrames.apply()
            }
            // A phone can add/delete local videos while another app is in front. Reuse only
            // the same library; its already-published card IDs avoid filesystem IO on this path.
            player?.takeIf { it.playbackState == Player.STATE_IDLE }?.let { parked ->
                val expected = if (Wallpapers.selectedId == Wallpapers.VIDEOS) {
                    Wallpapers.items.mapNotNull { it.id.takeIf { id -> id.startsWith(Wallpapers.VIDEO_PREFIX) }?.removePrefix(Wallpapers.VIDEO_PREFIX) }.toSet()
                } else setOf(Wallpapers.selectedId.removePrefix(Wallpapers.VIDEO_PREFIX))
                val loaded = (0 until parked.mediaItemCount).map { parked.getMediaItemAt(it).mediaId }.toSet()
                if (loaded != expected) release()
            }
            if (!home.frameReady) playbackRequestedAt = android.os.SystemClock.elapsedRealtime()
            val p = player ?: open()?.also { player = it; home.player = it } ?: return@LaunchedEffect
            DiagLog.i("video wallpaper starts (${p.currentMediaItem?.mediaId} at ${p.currentPosition} ms, idle $idle)")
            if (p.playbackState == Player.STATE_IDLE) p.prepare()
            p.playWhenReady = true
            val firstFrame = withTimeoutOrNull(8_000) { snapshotFlow { home.frameReady }.first { it } }
            if (firstFrame == null) {
                DiagLog.i("video wallpaper: no rendered first frame after 8 s")
                if (keepNow && playNow && recoveryAttempts < 2) {
                    recoveryAttempts++
                    release()
                    delay(800)
                    restart++
                }
                return@LaunchedEffect
            }
            cover.snapTo(1f)
        } else {
            val p = player ?: return@LaunchedEffect
            if (!keepNow) {
                if (cover.value > 0f && behindAppNow && leaveOnPicture(p)) return@LaunchedEffect
                cover.snapTo(0f)
                letGo(reuseForApp = behindAppNow)
                return@LaunchedEffect
            }
            p.pause()
            // Photo/clock savers need no video decoder. Retain a paused, prepared surface;
            // waking resumes immediately, without background decode or frame recapture.
            if (retainPaused) return@LaunchedEffect
            DiagLog.i("video wallpaper stops at ${p.currentPosition} ms")
            if (cover.value > 0f) {
                // The video surface already contains the exact frame. Keep it under an open
                // control center or wallpaper sheet, including TVs that cannot copy video.
                val film = p.currentMediaItem?.mediaId
                val at = p.currentPosition
                // The paused film stays while Home shows it plainly, as the aerial film does: no frame
                // read as Control Center opens or its keys stop and start the film, which on Hisense
                // VIDAA_TV MTK9652 failed the decoder and left Home black (issue #1251). It gives way
                // under a frosted panel, into an app, or once no key has come for KEEP_STOPPED_MS.
                val quiet = if (standInNow && film != null) untilQuietOrLeft({ standInNow && keepNow }, { lastInputNow }) else false
                if (!keepNow) {
                    // Into an app: on its frame if one comes in time, else let go at once and the
                    // picture catches up behind it.
                    if (behindAppNow && leaveOnPicture(p)) return@LaunchedEffect
                    cover.snapTo(0f)
                    letGo(reuseForApp = behindAppNow)
                    return@LaunchedEffect
                }
                val frame = film?.let { frameOf(it, at, held = true) }
                if (frame != null) Wallpapers.useLiveFrame(frame.first, film, frame.second)
                if (quiet) DiagLog.i("video wallpaper picture takes over from the stopped film")
                cover.animateTo(0f, tween(if (frame != null) 320 else 600, easing = Motion.EaseOut))
                if (quiet) {
                    release()
                    return@LaunchedEffect
                }
            }
            delay(KEEP_STOPPED_MS)
            release()
        }
    }
    LaunchedEffect(showing) {
        if (showing) { delay(30_000); recoveryAttempts = 0 }
    }
    LaunchedEffect(keep) {
        // Home in sight again: on the screensaver's last picture, if one of these videos played there.
        if (keep && player == null) VideoSaverFrames.apply()
    }
    DisposableEffect(Unit) { onDispose { release() } }

    AndroidView(
        modifier = Modifier.fillMaxSize().coverShape(if (AerialSettings.videoWallpaperFit) 0f else shape?.view ?: 0f),
        factory = { ctx ->
            val slot = FilmSlot(ctx)
            WallpaperReturnShield(ctx, slot).also { shield ->
            returnShield = shield
            shield.fallback(Wallpapers.backdrop?.asAndroidBitmap())
            surface = slot.surface
            slot.surface.holder.addCallback(object : android.view.SurfaceHolder.Callback {
                override fun surfaceCreated(holder: android.view.SurfaceHolder) = Unit
                override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, width: Int, height: Int) = Unit
                override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                    if (surface === slot.surface) {
                        coverReturn()
                        home.frameReady = false; showing = false
                        scope.launch { cover.snapTo(0f) }
                    }
                }
            })
            player?.setVideoSurfaceView(slot.surface)
        } },
        onRelease = {
            surface?.let { player?.clearVideoSurfaceView(it) }
            home.frameReady = false; showing = false; surface = null; returnShield = null
        },
    )
}

/** Whether a film of [shape] (width over height, 0 unknown) is the screen's shape, near enough. */
private fun screenShaped(shape: Float): Boolean =
    shape <= 0f || kotlin.math.abs(shape * Wallpapers.screenH / Wallpapers.screenW - 1f) < SHAPE_SLACK

/**
 * A film's width over its height as shown, and whether it is kept on its side with the turn in the
 * file (a phone's upright video). Its view takes its shape ([view]) only when turned: the decoders'
 * own cropping (SCALE_TO_FIT_WITH_CROPPING) fills the screen right with any film stored upright, but
 * a turned one Xiaomi MiTV-MFTR0 and TCL MT5879 turned and squeezed whole into 16:9. And a view
 * larger than the screen is not the rule: TCL MT5879's snapshot of Home, shown for a second as
 * Home comes back from an app, squeezed such a film whole into the screen.
 */
private data class FilmShape(val shape: Float, val turned: Boolean) {
    val view: Float get() = if (turned) shape else 0f
}

/** The film's view in [shape] (0 the screen's), as large as covers the screen and centred, the rest off it. */
private fun Modifier.coverShape(shape: Float): Modifier = if (screenShaped(shape)) this else layout { measurable, constraints ->
    val w = constraints.maxWidth
    val h = constraints.maxHeight
    val tall = shape < w.toFloat() / h
    val cw = if (tall) w else (h * shape).toInt()
    val ch = if (tall) (w / shape).toInt() else h
    val placeable = measurable.measure(Constraints.fixed(cw, ch))
    layout(w, h) { placeable.place((w - cw) / 2, (h - ch) / 2) }
}

/**
 * How the decoder scales a film of [shape] into its view. A turned one's view takes its shape
 * (coverShape): nothing to crop, and the decoder's cropping counted its sides before the turn,
 * zooming it in three times (Xiaomi MiTV-MFTR0).
 */
private fun scalingFor(shape: FilmShape?, fit: Boolean): Int =
    if (fit || !screenShaped(shape?.view ?: 0f)) C.VIDEO_SCALING_MODE_SCALE_TO_FIT else C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING

/** Each owner's video's shape once it has played, for this process. */
private val filmShapes = java.util.concurrent.ConcurrentHashMap<String, FilmShape>()

/** How far a film's shape may be from the screen's and still fill it as it is (1080 against 1088 lines). */
private const val SHAPE_SLACK = 0.02f

/**
 * The key frame of the owner's video [id] nearest [atMs] at [w]×[h], with its own moment, decoded
 * in the app (AerialStream.keyFrameOfFile); null where it cannot be (not MP4, not H.264 or HEVC,
 * no software decoder, a green frame).
 */
internal suspend fun keyFrameOfVideo(context: android.content.Context, id: String, atMs: Long, w: Int, h: Int): Pair<Bitmap, Long>? {
    val film = withContext(Dispatchers.IO) { ScreensaverVideos.all(context).firstOrNull { it.id == id } } ?: return null
    val started = android.os.SystemClock.elapsedRealtime()
    val frame = try {
        withTimeoutOrNull(VIDEO_KEY_FRAME_MS) { AerialStream.keyFrameOfFile(film.file, id, atMs, w, h) }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        DiagLog.i("video wallpaper key frame in the app failed after ${android.os.SystemClock.elapsedRealtime() - started} ms: ${e.javaClass.simpleName} ${e.message}")
        return null
    } ?: run {
        DiagLog.i("video wallpaper key frame in the app took over $VIDEO_KEY_FRAME_MS ms")
        return null
    }
    DiagLog.i("video wallpaper key frame decoded in the app in ${android.os.SystemClock.elapsedRealtime() - started} ms (${frame.codec} ${frame.decodeMs} ms, ${frame.timeMs - atMs} ms off)")
    // Green as a lost colour plane reads (see LiveWallpaper's keyFrameDecodedInApp); a dark frame
    // of a night film is the film's own.
    if (unreadable(frame.bitmap) == "green") {
        DiagLog.i("video wallpaper decoded frame is green; not used")
        return null
    }
    return frame.bitmap to frame.timeMs
}

/** The longest a key frame of the owner's video may take to decode in the app (a 4K HEVC one on a slow TV): then the next way. */
private const val VIDEO_KEY_FRAME_MS = 4_000L
