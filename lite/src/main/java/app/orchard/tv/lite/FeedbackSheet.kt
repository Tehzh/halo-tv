package app.orchard.tv.lite

import android.content.Context
import android.view.Gravity
import android.view.KeyEvent

class FeedbackSheet(context: Context, close: () -> Unit, notices: () -> Unit) : Sheet(context, close) {
    init {
        val panel = GlassBox(context, 24f)
        addView(panel, LayoutParams(Ui.pxi(810f), Ui.pxi(460f), Gravity.CENTER))
        panel.addView(FeedbackView(context, Ui.px(1f),
            phone = { PhoneServer.address(context) }, qr = { PhoneServer.qr(it, 4) }, notices = notices, close = close), LayoutParams(-1, -1))
    }
    // These are native Android buttons: let the actual View focus own D-pad input.
    override fun onKey(e: KeyEvent): Boolean {
        if (e.keyCode == KeyEvent.KEYCODE_BACK) { if (e.action == KeyEvent.ACTION_UP) close(); return true }
        return false
    }
}
