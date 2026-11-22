package app.orchard.tv.island

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

internal data class IslandOptions(
    val enabled: Boolean = false,
    val music: Boolean = true,
    val transfers: Boolean = true,
    val updates: Boolean = true,
    val musicPackage: String? = null,
    val position: IslandPosition = IslandPosition.Default,
)

internal class IslandPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("desktop_island", Context.MODE_PRIVATE)
    var options by mutableStateOf(
        IslandOptions(
            enabled = prefs.getBoolean("enabled", false),
            music = prefs.getBoolean("music", true),
            transfers = prefs.getBoolean("transfers", true),
            updates = prefs.getBoolean("updates", true),
            musicPackage = prefs.getString("music_package", null),
            position = IslandPosition.restore(prefs.getString("position", null)),
        ),
    )
        private set

    fun change(transform: (IslandOptions) -> IslandOptions) {
        val next = transform(options)
        if (next == options) return
        options = next
        prefs.edit {
            putBoolean("enabled", next.enabled)
            putBoolean("music", next.music)
            putBoolean("transfers", next.transfers)
            putBoolean("updates", next.updates)
            putString("music_package", next.musicPackage)
            putString("position", next.position.id)
        }
    }
}
