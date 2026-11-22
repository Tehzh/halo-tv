package app.orchard.tv.ui.control

/** The first focusable row on a restricted control center varies by device capability. */
internal enum class RestrictedFocusTarget { Volume, Brightness, Leave }

internal fun restrictedFocusTarget(showsVolume: Boolean, showsBrightness: Boolean): RestrictedFocusTarget = when {
    showsVolume -> RestrictedFocusTarget.Volume
    showsBrightness -> RestrictedFocusTarget.Brightness
    else -> RestrictedFocusTarget.Leave
}
