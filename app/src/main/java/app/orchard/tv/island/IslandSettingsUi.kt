package app.orchard.tv.island

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.orchard.tv.R
import app.orchard.tv.apps.AppEntry
import app.orchard.tv.system.SystemProbe
import app.orchard.tv.ui.components.Pressable
import app.orchard.tv.ui.components.settingsFrost
import app.orchard.tv.ui.theme.Glyph
import app.orchard.tv.ui.theme.GlyphIcon
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type

@Composable
internal fun IslandSettings(preferences: IslandPreferences, apps: List<AppEntry>, master: FocusRequester) {
    val context = LocalContext.current
    val options = preferences.options
    val sourceButton = remember { FocusRequester() }
    val candidates = remember(apps, context.packageName) {
        apps.asSequence()
            .filter { it.source == null && it.folder == null && it.shortcut == null && it.packageName != context.packageName }
            .distinctBy { it.packageName }.sortedBy { it.label.lowercase() }.toList()
    }
    var choosing by remember { mutableStateOf(false) }
    var choosingPosition by remember { mutableStateOf(false) }
    val positionButton = remember { FocusRequester() }
    var returnToPosition by remember { mutableStateOf(false) }
    var returnToSource by remember { mutableStateOf(false) }
    var allowed by remember { mutableStateOf(islandNotificationAccess(context)) }
    var couldNotOpen by remember { mutableStateOf(false) }
    val focused = LocalWindowInfo.current.isWindowFocused
    if (options.enabled && options.music) {
        DisposableEffect(context) {
            val remove = observeIslandAccess(context) { allowed = islandNotificationAccess(context) }
            allowed = islandNotificationAccess(context)
            onDispose { remove() }
        }
        LaunchedEffect(focused) { if (focused) allowed = islandNotificationAccess(context) }
    }

    if (choosing) {
        IslandAppPicker(candidates, options.musicPackage, onClose = { choosing = false; returnToSource = true }) { pkg ->
            preferences.change { it.copy(musicPackage = pkg) }
        }
    }

    if (choosingPosition) {
        IslandPositionPicker(options.position, onClose = { choosingPosition = false; returnToPosition = true }) { position ->
            preferences.change { it.copy(position = position) }
        }
    }

    LaunchedEffect(returnToPosition) {
        if (returnToPosition) {
            withFrameNanos { }
            runCatching { positionButton.requestFocus() }
            returnToPosition = false
        }
    }

    LaunchedEffect(returnToSource) {
        if (returnToSource) {
            withFrameNanos { }
            runCatching { sourceButton.requestFocus() }
            returnToSource = false
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        BasicText(stringResource(R.string.island_title), style = Type.Title.copy(fontWeight = FontWeight.SemiBold))
        IslandOption(R.string.island_enabled, options.enabled, master) { enabled -> preferences.change { it.copy(enabled = enabled) } }
        BasicText(
            stringResource(R.string.island_cost) + "\n" + stringResource(R.string.island_playback_hint),
            style = Type.Caption.copy(color = Palette.SecondaryLabel),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
        )
        if (options.enabled) {
            IslandOption(R.string.island_music, options.music) { enabled -> preferences.change { it.copy(music = enabled) } }
            if (options.music) {
                val selected = candidates.firstOrNull { it.packageName == options.musicPackage }?.label
                IslandSettingRow(stringResource(R.string.island_source), selected ?: stringResource(R.string.island_no_source),
                    sourceButton, arrow = true, onClick = { choosing = true })
                IslandSettingRow(
                    stringResource(R.string.island_access),
                    stringResource(if (allowed) R.string.island_access_on else R.string.island_access_off),
                    arrow = true,
                ) {
                    couldNotOpen = !runCatching {
                        val intent = SystemProbe.revivalSettingsIntent(context) ?: return@runCatching false
                        context.startActivity(intent)
                        true
                    }.getOrDefault(false)
                }
                BasicText(stringResource(if (couldNotOpen) R.string.island_access_manual else R.string.island_access_hint),
                    style = Type.Caption.copy(color = Palette.SecondaryLabel),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
            }
            IslandSettingRow(
                stringResource(R.string.island_position), stringResource(options.position.label), positionButton,
                arrow = true, onClick = { choosingPosition = true },
            )
            IslandOption(R.string.island_transfers, options.transfers) { enabled -> preferences.change { it.copy(transfers = enabled) } }
            IslandOption(R.string.island_updates, options.updates) { enabled -> preferences.change { it.copy(updates = enabled) } }
        }
    }
}

@Composable
private fun IslandOption(label: Int, current: Boolean, requester: FocusRequester? = null, choose: (Boolean) -> Unit) {
    IslandSettingRow(stringResource(label), stringResource(if (current) R.string.st_on else R.string.st_off), requester) {
        choose(!current)
    }
}

@Composable
private fun IslandPositionPicker(current: IslandPosition, onClose: () -> Unit, onChoose: (IslandPosition) -> Unit) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.widthIn(max = 360.dp).fillMaxWidth().settingsFrost(22.dp).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)) {
            BasicText(stringResource(R.string.island_position), style = Type.Title.copy(fontWeight = FontWeight.SemiBold))
            IslandPosition.entries.forEach { position ->
                IslandSettingRow(
                    stringResource(position.label), requester = if (position == current) first else null, check = position == current,
                ) { onChoose(position); onClose() }
            }
        }
        LaunchedEffect(Unit) { withFrameNanos { }; first.requestFocus() }
    }
}

@Composable
private fun IslandAppPicker(apps: List<AppEntry>, selected: String?, onClose: () -> Unit, onChoose: (String?) -> Unit) {
    val first = remember { FocusRequester() }
    val cancel = remember { FocusRequester() }
    val initialApp = remember(apps, selected) { apps.indexOfFirst { it.packageName == selected }.coerceAtLeast(0) }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = initialApp)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.widthIn(max = 440.dp).fillMaxWidth()
                .settingsFrost(22.dp).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText(stringResource(R.string.island_source), style = Type.Title.copy(fontWeight = FontWeight.SemiBold))
            if (apps.isEmpty()) BasicText(stringResource(R.string.island_empty_sources), style = Type.Caption)
            LazyColumn(state = list, modifier = Modifier.heightIn(max = 324.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)) {
                itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                    IslandSettingRow(app.label, requester = if (index == initialApp) first else null,
                        check = selected == app.packageName) {
                        onChoose(app.packageName)
                        onClose()
                    }
                }
                item {
                    IslandSettingRow(stringResource(R.string.island_no_source), requester = if (apps.isEmpty()) first else null,
                        check = selected == null) {
                        onChoose(null)
                        onClose()
                    }
                }
            }
            IslandSettingRow(stringResource(android.R.string.cancel), requester = cancel, onClick = onClose)
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            if (!runCatching { first.requestFocus() }.getOrDefault(false)) cancel.requestFocus()
        }
    }
}

@Composable
private fun IslandSettingRow(
    title: String,
    value: String? = null,
    requester: FocusRequester? = null,
    arrow: Boolean = false,
    check: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Pressable(onClick, Modifier.fillMaxWidth(), focusRequester = requester, focusedScale = 1f) { state ->
        val fg = if (state.focused) Palette.OnFocus else Palette.Label
        Row(
            Modifier.fillMaxWidth().heightIn(min = 42.dp).clip(shape)
                .background(if (state.focused) Palette.Focus else Color.White.copy(alpha = 0.075f))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText(title, Modifier.weight(1f), style = Type.Body.copy(color = fg), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (value != null) BasicText(value, Modifier.widthIn(max = 210.dp),
                style = Type.Caption.copy(color = fg), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (arrow || check) GlyphIcon(if (arrow) Glyph.ChevronRight else Glyph.Check, fg, 14.dp)
        }
    }
}
