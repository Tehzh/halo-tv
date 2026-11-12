package app.orchard.tv.ui.home

/** Invalidates asynchronous frame work whenever a player or its media changes. Main-thread only. */
internal class WallpaperFrameEpoch {
    data class Token(val epoch: Long, val mediaId: String?)
    private var epoch = 0L
    private var mediaId: String? = null
    fun begin(id: String?): Token { epoch++; mediaId = id; return current() }
    fun invalidate() { epoch++; mediaId = null }
    fun current() = Token(epoch, mediaId)
    fun accepts(token: Token): Boolean = token.mediaId != null && token == current()
}
