package app.orchard.tv.lite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.orchard.tv.lite.Ui.textPx

/** Fixed, lightweight surfaces shared by all three settings columns. */
internal fun fixedSettingsPlate(radius: Float = 20f, color: Int = 0xFF29394D.toInt()) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = Ui.px(radius)
    setStroke(Ui.pxi(0.6f).coerceAtLeast(1), 0x18FFFFFF)
}

internal object LiteSettingsGeometry {
    const val OUTER = 28f
    const val NAV = 180f
    const val PHONE = 206f
    const val GAP = 16f
    const val CONTENT_PAD = 12f
    fun contentWidth() = Ui.screenW - Ui.pxi(OUTER * 2 + NAV + PHONE + GAP * 2 + CONTENT_PAD * 2)
}

/** One persistent local-network QR card. It has no animation and retries only while offline. */
internal class LitePhoneCard(context: Context, private val compact: Boolean = false) : LinearLayout(context) {
    private val qr = ImageView(context)
    private val address = context.label("", 10f, 500).apply { visibility = View.GONE }
    private val hint = context.label(context.getString(R.string.wp_phone_offline), 10f, 400, Ui.SECONDARY)
    private var picture: Bitmap? = null
    private var attached = false
    private var generation = 0
    private val retry = Runnable { if (attached) load() }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(Ui.pxi(14f), Ui.pxi(16f), Ui.pxi(14f), Ui.pxi(16f))
        @Suppress("DEPRECATION")
        setBackgroundDrawable(fixedSettingsPlate(if (compact) 16f else 20f))
        if (Build.VERSION.SDK_INT >= 29) isForceDarkAllowed = false
        if (!compact) {
            addView(context.label(context.getString(R.string.wp_phone_title), 15f, 600).apply {
                setSingleLine(false); maxLines = 2; gravity = Gravity.CENTER
            }, LayoutParams(-1, -2).apply { bottomMargin = Ui.pxi(14f) })
        }
        val white = FrameLayout(context).apply {
            val pad = Ui.pxi(10f)
            setPadding(pad, pad, pad, pad)
            @Suppress("DEPRECATION")
            setBackgroundDrawable(GradientDrawable().apply { setColor(-1); cornerRadius = Ui.px(14f) })
        }
        qr.scaleType = ImageView.ScaleType.FIT_CENTER
        qr.contentDescription = context.getString(R.string.wp_phone_title)
        white.addView(qr, FrameLayout.LayoutParams(-1, -1))
        val size = Ui.pxi(if (compact) 112f else 136f)
        addView(white, LayoutParams(size, size))
        if (!compact) {
            addView(context.label(context.getString(R.string.halo_phone_features), 11f, 500).apply {
                setSingleLine(false); gravity = Gravity.CENTER; setLineSpacing(Ui.px(4f), 1f)
            }, LayoutParams(-1, -2).apply { topMargin = Ui.pxi(14f) })
            addView(View(context).apply { setBackgroundColor(Ui.HAIRLINE) }, LayoutParams(-1, 1).apply {
                topMargin = Ui.pxi(16f); bottomMargin = Ui.pxi(12f)
            })
            addView(context.label(context.getString(R.string.halo_phone_tips), 14f, 600).apply {
                gravity = Gravity.CENTER
            }, LayoutParams(-1, -2).apply { bottomMargin = Ui.pxi(8f) })
            listOf(Glyph.Wifi to R.string.halo_phone_same_network, Glyph.Globe to R.string.halo_phone_browser).forEach { (glyph, label) ->
                val line = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
                line.addView(ImageView(context).apply { setImageBitmap(Glyphs.image(glyph, Ui.pxi(14f), Ui.SECONDARY)) }, LayoutParams(Ui.pxi(14f), Ui.pxi(14f)))
                line.addView(context.label(context.getString(label), 10f, 400, Ui.SECONDARY).apply {
                    setSingleLine(false); maxLines = 2
                }, LayoutParams(0, -2, 1f).apply { leftMargin = Ui.pxi(7f) })
                addView(line, LayoutParams(-1, -2).apply { bottomMargin = Ui.pxi(7f) })
            }
        }
        hint.apply { setSingleLine(false); maxLines = 3; gravity = Gravity.CENTER }
        addView(hint, LayoutParams(-1, -2).apply { topMargin = Ui.pxi(8f) })
        address.apply {
            setSingleLine(false); maxLines = 2; gravity = Gravity.CENTER; includeFontPadding = false
            setPadding(Ui.pxi(6f), Ui.pxi(8f), Ui.pxi(6f), Ui.pxi(8f))
            @Suppress("DEPRECATION")
            setBackgroundDrawable(fixedSettingsPlate(10f, 0xFF3A4C62.toInt()))
        }
        addView(address, LayoutParams(-1, -2).apply { topMargin = Ui.pxi(10f) })
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow(); attached = true; load()
    }

    private fun load() {
        removeCallbacks(retry)
        val request = ++generation
        Thread({
            val url = PhoneServer.address(context.applicationContext)
            val bitmap = url?.let { runCatching { PhoneServer.qr(it) }.getOrNull() }
            post {
                if (!attached || request != generation) { bitmap?.recycle(); return@post }
                val old = picture
                picture = bitmap
                qr.setImageDrawable(bitmap?.let { BitmapDrawable(resources, it).apply { setFilterBitmap(false) } })
                old?.recycle()
                hint.visibility = if (url == null || bitmap == null) VISIBLE else GONE
                address.text = url?.removePrefix("http://").orEmpty()
                address.visibility = if (url == null) GONE else VISIBLE
                if (url == null || bitmap == null) postDelayed(retry, 5000)
            }
        }, "HaloLite-QR").start()
    }

    override fun onDetachedFromWindow() {
        attached = false; generation++; removeCallbacks(retry)
        qr.setImageDrawable(null); picture?.recycle(); picture = null
        super.onDetachedFromWindow()
    }
}
