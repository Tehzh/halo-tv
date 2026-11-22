package app.orchard.tv.wallpaper

/** Capture intent before network I/O, including leaving and returning to the same wallpaper ID. */
internal class OnlineWallpaperRequests {
    data class Ticket(val number: Long, val selectedId: String, val selection: Long)
    private var sequence = 0L
    private var manual: Long? = null

    @Synchronized fun begin(id: String, selection: Long, explicit: Boolean = true): Ticket? {
        if (!explicit && manual != null) return null
        return Ticket(++sequence, id, selection).also { if (explicit) manual = it.number }
    }
    @Synchronized fun accepts(ticket: Ticket, id: String, selection: Long) =
        ticket.number == sequence && ticket.selectedId == id && ticket.selection == selection
    @Synchronized fun finish(ticket: Ticket) {
        if (manual == ticket.number) manual = null
        if (sequence == ticket.number) sequence++
    }
}
