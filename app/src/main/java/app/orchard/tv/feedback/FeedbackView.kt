package app.orchard.tv.feedback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.AsyncTask
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.orchard.tv.R

/** D-pad navigation and a phone handoff, shared with Android 4.x. */
@Suppress("DEPRECATION")
class FeedbackView(
    context: Context,
    private val unit: Float,
    phone: () -> String?,
    qr: (String) -> Bitmap,
    notices: () -> Unit,
    close: () -> Unit,
) : LinearLayout(context) {
    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(px(28), px(20), px(28), px(20))

        val content = LinearLayout(context).apply {
            orientation = VERTICAL
            addView(label(R.string.halo_feedback_entry, 26))
            addView(label(R.string.halo_feedback_github_hint, 18), spaced(12))
            addView(label(R.string.halo_feedback_public_hint, 15), spaced(8))
            addView(action(R.string.halo_feedback_notices, notices), spaced(16, 44))
            addView(action(R.string.halo_feedback_back, close), spaced(8, 44))
        }
        val scroll = ScrollView(context).apply {
            isFocusable = false
            addView(content)
        }
        addView(scroll, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val aside = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            setPadding(px(18), 0, 0, 0)
            val image = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
            val caption = label(R.string.halo_feedback_github_scan, 16).apply { gravity = Gravity.CENTER }
            addView(image, LayoutParams(px(174), px(174)))
            addView(caption, spaced(10))
            AsyncTask.THREAD_POOL_EXECUTOR.execute {
                val phoneUrl = runCatching(phone).getOrNull()
                val bitmap = qr(if (phoneUrl == null) GithubFeedback.URL else phoneUrl + GithubFeedback.PHONE_PATH)
                val hint = if (phoneUrl == null) R.string.halo_feedback_github_scan else R.string.halo_feedback_diagnostics_scan
                image.post {
                    image.setImageBitmap(bitmap)
                    image.contentDescription = context.getString(hint)
                    caption.setText(hint)
                }
            }
            addView(TextView(context).apply {
                text = "github.com/Null9797/halo-tv/issues"
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, 12 * unit)
            }, spaced(6))
        }
        addView(aside, LayoutParams(px(248), ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun px(dp: Int) = (dp * unit + 0.5f).toInt()

    private fun spaced(top: Int, height: Int = 0) =
        LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, if (height == 0) ViewGroup.LayoutParams.WRAP_CONTENT else px(height)).apply {
            topMargin = px(top)
        }

    private fun label(id: Int, size: Int) = TextView(context).apply {
        setText(id)
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, size * unit)
    }

    @Suppress("DEPRECATION")
    private fun action(id: Int, onClick: () -> Unit) = Button(context).apply {
        setText(id)
        isAllCaps = false
        isFocusable = true
        setTextSize(TypedValue.COMPLEX_UNIT_PX, 17 * unit)
        fun style(focused: Boolean) {
            setTextColor(if (focused) 0xFF152239.toInt() else Color.WHITE)
            setBackgroundDrawable(GradientDrawable().apply {
                cornerRadius = 12 * unit
                setColor(if (focused) Color.WHITE else 0x334C6290)
                setStroke(px(if (focused) 3 else 1), if (focused) 0xFF81BAFF.toInt() else 0x337D9AB4)
            })
        }
        style(false)
        setOnFocusChangeListener { _, focused -> style(focused) }
        setOnClickListener { onClick() }
    }
}
