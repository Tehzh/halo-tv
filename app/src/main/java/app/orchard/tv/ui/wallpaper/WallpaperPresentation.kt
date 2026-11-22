package app.orchard.tv.ui.wallpaper

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.orchard.tv.Overlay

/** AnimatedVisibility still draws this page after Home or Back has cleared the current overlay. */
@Composable
internal fun rememberPresentedWallpaperPicker(
    visible: Boolean,
    picker: Overlay.WallpaperPicker?,
): Overlay.WallpaperPicker? {
    var lastVisiblePicker by remember { mutableStateOf<Overlay.WallpaperPicker?>(null) }
    SideEffect { if (visible) lastVisiblePicker = picker }
    return if (visible) picker else lastVisiblePicker
}
