package app.orchard.tv.lite

import android.content.Context
import android.content.pm.ApplicationInfo
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast

/** Local app details keep uninstall reachable even when OEM app management opens an error page. */
internal class AppInfoSheet(
    context: Context,
    close: () -> Unit,
    name: String,
    pkg: String,
) : Sheet(context, close) {
    init {
        addView(View(context).apply { setBackgroundColor(0x73000000) }, LayoutParams(-1, -1))
        val panel = GlassBox(context, 24f)
        val height = (Ui.screenH - Ui.pxi(64f)).coerceAtLeast(Ui.pxi(200f))
        addView(panel, LayoutParams(Ui.pxi(560f), height, Gravity.CENTER))
        val clip = Viewport(context)
        panel.addView(clip, LayoutParams(-1, -1))
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.pxi(24f), Ui.pxi(20f), Ui.pxi(24f), Ui.pxi(20f))
        }
        clip.addView(col, LayoutParams(-1, -2))
        scroller = col
        viewport = height
        fun button(glyph: Glyph, label: String, destructive: Boolean = false, action: () -> Unit) {
            val control = MenuRow(context, glyph, label, destructive = destructive).apply { this.action = action }
            col.addView(control, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.pxi(12f) })
            row(control)
        }
        // Back receives initial focus: a key release must never select an uninstall action.
        button(Glyph.ChevronLeft, context.getString(R.string.menu_info), action = close)
        val info = runCatching { context.packageManager.getPackageInfo(pkg, 0) }.getOrNull()
        val app = info?.applicationInfo
        val removable = app != null && app.flags and ApplicationInfo.FLAG_SYSTEM == 0
        val hint = if (removable) context.getString(R.string.lite_system_app_info_hint) else ""
        val description = listOf(name, pkg, info?.versionName.orEmpty(), hint)
            .filter { it.isNotEmpty() }.joinToString("\n\n")
        col.addView(context.label(description, 17f, 400, Ui.WHITE).apply {
            setSingleLine(false)
            ellipsize = null
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.pxi(18f) })
        if (removable) {
            button(Glyph.Trash, context.getString(R.string.menu_uninstall), destructive = true) {
                if (!SystemIntents.requestUninstall(context, pkg)) {
                    Toast.makeText(context, R.string.lite_system_uninstall_unavailable, Toast.LENGTH_LONG).show()
                }
            }
        }
        button(Glyph.Info, context.getString(R.string.lite_system_app_details)) {
            if (!SystemIntents.openApplicationDetails(context, pkg)) {
                Toast.makeText(context, R.string.lite_system_app_details_unavailable, Toast.LENGTH_LONG).show()
            }
        }
        button(Glyph.Gear, context.getString(R.string.cc_system_settings)) {
            if (!SystemIntents.openSettings(context)) {
                Toast.makeText(context, R.string.lite_system_settings_unavailable, Toast.LENGTH_LONG).show()
            }
        }
        focusFirst()
    }
}
