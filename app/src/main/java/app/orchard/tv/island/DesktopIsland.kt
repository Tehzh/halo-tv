package app.orchard.tv.island

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.orchard.tv.R
import app.orchard.tv.ui.components.Pressable
import app.orchard.tv.ui.theme.Glyph
import app.orchard.tv.ui.theme.GlyphIcon
import app.orchard.tv.ui.theme.Type
import app.orchard.tv.update.Updates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal fun islandOnHome(
    resumed: Boolean,
    windowFocused: Boolean,
    overlayOpen: Boolean,
    saver: Boolean,
    restricted: Boolean,
    onShelf: Boolean,
): Boolean = resumed && windowFocused && !overlayOpen && !saver && !restricted && onShelf

internal class IslandFocus {
    val requester = FocusRequester()
    var available = false
    private var focused = false
    private var pendingRestore = false

    fun dockUp(settings: FocusRequester): FocusRequester = if (available) settings else FocusRequester.Default

    fun settingsUp(): FocusRequester = if (available) requester else FocusRequester.Default

    fun settingsDown(dock: FocusRequester): FocusRequester = if (available) dock else FocusRequester.Default

    fun onFocus(gained: Boolean, stillAvailable: Boolean) {
        if (gained || stillAvailable) focused = gained
    }

    fun becameUnavailable() {
        if (focused) pendingRestore = true
        focused = false
    }

    fun restoreOnHome(home: Boolean): Boolean {
        if (!home || !pendingRestore) return false
        pendingRestore = false
        return true
    }
}

private data class NowPlaying(val track: IslandTrack, val artwork: ImageBitmap?)

@Composable
internal fun DesktopIsland(
    home: Boolean,
    preferences: IslandPreferences,
    focus: IslandFocus,
    downTarget: FocusRequester,
    restoreFocus: () -> Unit,
    openTask: (IslandTask) -> Unit,
    modifier: Modifier = Modifier,
    musicOpened: () -> Unit = {},
    widthLimit: Float = 420f,
    placement: (Size) -> Rect? = { Rect(0f, 0f, it.width, it.height) },
    surface: () -> IslandSurface = { IslandSurface() },
) {
    val context = LocalContext.current
    val app = context.applicationContext
    val options = preferences.options
    val active = home && options.enabled
    val source = options.musicPackage?.takeIf { active && options.music }
    var playing by remember(source) { mutableStateOf<NowPlaying?>(null) }
    if (source != null) IslandWhileResumed(source, clear = { playing = null }) {
        islandTracks(app, source).conflate().collectLatest { track ->
            if (track == null) {
                playing = null
            } else {
                // Show the title immediately, but never let a previous track's cover outlive it.
                playing = NowPlaying(track, artwork = null)
                val bitmap = withContext(Dispatchers.Default) { track.art(app, source)?.asImageBitmap() }
                playing = NowPlaying(track, bitmap)
            }
        }
    }

    val transferEnabled = active && options.transfers
    var transfer by remember(transferEnabled) { mutableStateOf<IslandProgress?>(null) }
    if (transferEnabled) IslandWhileResumed(Unit, clear = { transfer = null }) {
        islandTransfers.observe().collectLatest { event ->
            transfer = event
            event?.remaining(SystemClock.elapsedRealtime())?.let {
                delay(it)
                transfer = null
            }
        }
    }

    val updateEnabled = active && options.updates
    var update by remember(updateEnabled) { mutableStateOf<IslandProgress?>(null) }
    if (updateEnabled) IslandWhileResumed(Unit, clear = { update = null }) {
        val progress = IslandUpdateProgress()
        snapshotFlow { Updates.install }.map { install ->
            // Coalesce percentage changes while keeping every state transition.
            if (install is Updates.Install.Downloading) {
                val step = (install.fraction * 20).toInt()
                Updates.Install.Downloading(step.coerceIn(0, 20) / 20f)
            } else install
        }.distinctUntilChanged().collectLatest { install ->
            update = when (install) {
                is Updates.Install.Downloading -> progress.onDownload(install.fraction)
                Updates.Install.Ready -> progress.onReady(SystemClock.elapsedRealtime())
                else -> null.also { progress.reset() }
            }
            update?.remaining(SystemClock.elapsedRealtime())?.let {
                delay(it)
                update = null
                progress.reset()
            }
        }
    }

    val task = preferredTask(transfer, update)
    val title = if (!active) null else when (task?.task) {
        null -> playing?.track?.title
        IslandTask.Photos -> stringResource(if (task.finishedAt == null) R.string.island_receiving_photos else R.string.island_received_photos)
        IslandTask.Video -> stringResource(if (task.finishedAt == null) R.string.island_receiving_video else R.string.island_received_video)
        IslandTask.Update -> stringResource(if (task.finishedAt == null) R.string.island_downloading else R.string.island_update_ready)
    }
    val progress = task?.percent?.let { stringResource(R.string.island_percent, it) }
    val canOpen = task?.finishedAt != null || task == null && playing != null
    val cover = if (options.position == IslandPosition.LowerLeft) 24f else 22f
    val area = if (title != null) {
        placement(islandContentSize(title, progress, options.position, cover, canOpen, widthLimit))
    } else null
    val actionable = area != null && canOpen
    val stillAvailable by rememberUpdatedState(actionable)
    val returnFocus by rememberUpdatedState(restoreFocus)
    val stillHome by rememberUpdatedState(home)
    SideEffect { focus.available = actionable }
    LaunchedEffect(actionable, home) {
        if (!actionable) focus.becameUnavailable()
        if (home) {
            withFrameNanos { }
            if (focus.restoreOnHome(stillHome)) returnFocus()
        }
    }

    if (area == null || title == null) return
    val material = surface()
    val glyph = when (task?.task) {
        IslandTask.Photos -> Glyph.Photo
        IslandTask.Video, null -> Glyph.Play
        IslandTask.Update -> Glyph.Download
    }
    val artwork = playing?.artwork.takeIf { task == null }
    val shape = RoundedCornerShape(percent = 50)
    val coverSize = minOf(cover, area.height - 14f).dp
    Pressable(
        enabled = actionable,
        focusedScale = 1f,
        focusRequester = focus.requester,
        modifier = modifier.absoluteOffset(x = area.left.dp, y = area.top.dp)
            .focusProperties { down = downTarget }
            .onFocusChanged { focus.onFocus(it.isFocused, stillAvailable) },
        onClick = {
            when {
                task != null && task.finishedAt != null &&
                    (task.remaining(SystemClock.elapsedRealtime()) ?: 0L) > 0L &&
                    (task.task != IslandTask.Update || Updates.install == Updates.Install.Ready) -> openTask(task.task)
                task == null && source != null -> playing?.let {
                    if (openIslandMusic(context, source, it.track.controller)) musicOpened()
                }
            }
        },
    ) { state ->
        val fg = if (state.focused) Color(0xFF192331) else Color.White
        Row(
            Modifier.width(area.width.dp).height(area.height.dp).clip(shape)
                .islandMaterial(material, area, state.focused)
                .border(if (state.focused) 2.dp else 1.dp,
                    if (state.focused) Color(0xFF79C8FF) else Color(0x50FFFFFF), shape)
                .padding(horizontal = IslandPad.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IslandGap.dp),
        ) {
            Box(Modifier.size(cover.dp), contentAlignment = Alignment.Center) {
                if (artwork != null) Image(artwork, null, Modifier.size(coverSize).clip(RoundedCornerShape(5.dp)), contentScale = ContentScale.Crop)
                else GlyphIcon(glyph, fg, minOf(17.dp, coverSize))
            }
            BasicText(title, Modifier.weight(1f), style = Type.Body.copy(color = fg),
                overflow = TextOverflow.Ellipsis)
            progress?.let { BasicText(it, style = Type.Caption.copy(color = fg)) }
            if (actionable) GlyphIcon(Glyph.ChevronRight, fg, IslandArrow.dp)
        }
    }
}


private const val IslandPad = 10f
private const val IslandGap = 7f
private const val IslandArrow = 13f

@Composable
internal fun islandContentSize(
    title: String, progress: String?, position: IslandPosition, cover: Float, canOpen: Boolean, limit: Float,
): Size {
    val text = rememberTextMeasurer()
    val density = LocalDensity.current
    val titleStyle = Type.Body
    val progressStyle = Type.Caption
    return remember(text, density, title, titleStyle, progress, progressStyle, position, cover, canOpen, limit) {
        with(density) {
            val trail = (if (canOpen) IslandArrow + IslandGap else 0f) + (progress?.let {
                text.measure(AnnotatedString(it), style = progressStyle, maxLines = 1).size.width.toDp().value + IslandGap
            } ?: 0f)
            val chrome = IslandPad * 2 + cover + IslandGap + trail
            val available = (limit - chrome - 2f).coerceAtLeast(1f).dp.roundToPx().coerceAtLeast(1)
            val label = text.measure(AnnotatedString(title), style = titleStyle,
                constraints = Constraints(maxWidth = available), overflow = TextOverflow.Ellipsis)
            Size(
                (chrome + label.size.width.toDp().value + 2f).coerceAtLeast(65f),
                maxOf(position.height, label.size.height.toDp().value + 12f),
            )
        }
    }
}

/** An activity can pause before Compose draws again. Cancel subscriptions at the lifecycle event. */
@Composable
private fun IslandWhileResumed(key: Any, clear: () -> Unit, collect: suspend CoroutineScope.() -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, key) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                collect()
            } finally {
                clear()
            }
        }
    }
}
