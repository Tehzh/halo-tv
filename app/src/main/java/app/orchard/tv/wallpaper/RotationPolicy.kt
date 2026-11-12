package app.orchard.tv.wallpaper

/** -1 is an event, never a timer interval. Zero is always disabled. */
internal fun rotationDue(minutes: Int, dueAt: Long, now: Long, returnedHome: Boolean = false, applyNow: Boolean = false): Boolean =
    minutes != 0 && (applyNow || if (minutes == -1) returnedHome else dueAt > 0 && now >= dueAt)
