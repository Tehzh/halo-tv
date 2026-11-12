package app.orchard.tv.ui.home

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import app.orchard.tv.R
import app.orchard.tv.settings.AerialSettings
import kotlin.math.roundToInt

/** The same TV-safe quiz is used on Home and over a video app, without replacing its task. */
internal class QuizPanel(
    context: Context,
    private val onPassed: (Int) -> Unit,
    private val onParent: QuizPanel.() -> Unit,
) : FrameLayout(context) {
    private var questions = newRound()
    private var index = 0
    private var correct = 0
    private var points = 0
    private val selected = mutableSetOf<Int>()
    private var scale = 1f
    private var initialFocus: View? = null
    private var finished = false
    private var showingPin = false
    private var pinExit: (() -> Unit)? = null
    private var parentReturn: View? = null

    init {
        clipChildren = false
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xffe8dcf5.toInt(), 0xfff0e8fa.toInt(), 0xffe8ddf7.toInt()))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        scale = minOf(w / 960f, h / 540f)
        // A centered 960 x 540 design canvas keeps identical proportions on TV and app overlays.
        post { if (showingPin) renderParentPin() else render() }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus && !hasFocus()) initialFocus?.requestFocus()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        event.keyCode == KeyEvent.KEYCODE_BACK || super.dispatchKeyEvent(event)

    private fun unit(value: Int) = (value * scale).roundToInt().coerceAtLeast(1)

    private fun newRound(): List<QuizQuestion> {
        val prefs = context.getSharedPreferences("halo_quiz_history", Context.MODE_PRIVATE)
        val seen = prefs.getStringSet("seen", emptySet()).orEmpty()
        return QuizBank.round(seen).also { round ->
            prefs.edit().putStringSet("seen", seen + round.map { it.prompt }).apply()
        }
    }

    private fun surface(fill: Int, radius: Int, border: Int? = null, stroke: Int = 3) = GradientDrawable().apply {
        cornerRadius = unit(radius).toFloat()
        setColor(fill)
        if (border != null) setStroke(unit(stroke), border)
    }

    private fun label(value: CharSequence, size: Int, color: Int = INK, bold: Boolean = false) = TextView(context).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_PX, unit(size).toFloat())
        setTextColor(color)
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        includeFontPadding = false
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun fitText(view: TextView, min: Int, max: Int) {
        if (Build.VERSION.SDK_INT >= 26) view.setAutoSizeTextTypeUniformWithConfiguration(unit(min), unit(max), 1, TypedValue.COMPLEX_UNIT_PX)
        else view.setTextSize(TypedValue.COMPLEX_UNIT_PX, unit(min).toFloat())
    }

    private fun frame() = FrameLayout(context).apply { clipChildren = false; clipToPadding = false }

    private fun place(parent: FrameLayout, view: View, x: Int, y: Int, w: Int, h: Int) {
        parent.addView(view, LayoutParams(unit(w), unit(h)).apply { leftMargin = unit(x); topMargin = unit(y) })
    }

    private fun art(resource: Int) = ImageView(context).apply {
        setImageResource(resource)
        scaleType = ImageView.ScaleType.FIT_CENTER
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun button(value: String, action: () -> Unit) = Button(context).apply {
        id = generateViewId()
        isFocusableInTouchMode = true
        text = value
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_PX, unit(25).toFloat())
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(0xffffffff.toInt())
        gravity = Gravity.CENTER
        minimumHeight = 0; minHeight = 0; minimumWidth = 0; minWidth = 0
        includeFontPadding = false
        setPadding(unit(12), 0, unit(12), 0)
        stateListAnimator = null
        backgroundTintList = null
        background = StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), surface(0xff9d8ed5.toInt(), 18))
            addState(intArrayOf(android.R.attr.state_pressed), surface(0xff66519f.toInt(), 18, FOCUS))
            addState(intArrayOf(android.R.attr.state_focused), surface(PURPLE, 18, FOCUS))
            addState(intArrayOf(), surface(PURPLE, 18))
        }
        setOnClickListener { action() }
    }

    private fun focus(view: View) {
        initialFocus = view
        view.post { if (view.isAttachedToWindow) view.requestFocus() }
    }

    private fun canvas(): FrameLayout {
        removeAllViews()
        return frame().also { addView(it, LayoutParams(unit(960), unit(540), Gravity.CENTER)) }
    }

    private fun render() {
        showingPin = false
        val page = canvas()
        val result = index == questions.size
        val card = frame().apply { background = surface(0xeefcf9ff.toInt(), 28) }
        val side = frame().apply { background = surface(0xcdfaf6ff.toInt(), 28, 0x70ffffff, 1) }
        place(page, card, 34, 29, 596, 445)
        place(page, side, 646, 29, 286, 445)

        place(card, art(R.drawable.quiz_title_blocks), 19, 14, 39, 33)
        place(card, label(context.getString(R.string.quiz_title), 32, bold = true), 68, 10, 334, 43)
        if (!result) place(card, label(context.getString(R.string.quiz_position, index + 1, questions.size), 19).apply {
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
        }, 418, 17, 156, 28)
        val progressWidth = (540 - (questions.size - 1) * 5) / questions.size
        repeat(questions.size) { n ->
            place(card, View(context).apply {
                background = surface(if (n <= index) 0xff8370b7.toInt() else 0xffd8cde9.toInt(), 5)
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }, 33 + n * (progressWidth + 5), 59, progressWidth, 9)
        }
        if (!result) place(card, label(context.getString(R.string.quiz_goal), 18), 33, 79, 540, 30)

        place(side, art(R.drawable.quiz_star_blocks), 37, 9, 212, 153)
        place(side, label(context.getString(R.string.quiz_rest_title), 28, bold = true).apply {
            maxLines = 1; fitText(this, 20, 28)
        }, 30, 175, 242, 47)
        place(side, label(context.getString(R.string.quiz_rest_message_playful), 20).apply {
            gravity = Gravity.TOP
            setLineSpacing(unit(7).toFloat(), 1f)
            fitText(this, 16, 20)
        }, 25, 227, 247, 124)
        val parent = button(context.getString(R.string.kids_parent)) { onParent(this) }
        place(side, parent, 15, 373, 255, 54)
        if (result) renderResult(card, parent) else renderQuestion(card, questions[index], parent)
        renderRemoteHint(page)
    }

    private fun renderRemoteHint(page: FrameLayout) {
        place(page, art(R.drawable.quiz_remote_pad), 310, 482, 45, 44)
        place(page, label(context.getString(R.string.quiz_remote_hint), 20).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            fitText(this, 14, 20)
        }, 365, 482, 229, 44)
        place(page, label("OK", 18, 0xffffffff.toInt(), bold = true).apply {
            gravity = Gravity.CENTER
            background = surface(0xff61509c.toInt(), 22, 0xfffaf6ff.toInt(), 2)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, 612, 486, 38, 38)
    }

    private inner class AnswerTile(private val letter: Char, private val answer: String, color: Int, multiple: Boolean, action: () -> Unit) : FrameLayout(context) {
        private val badge = label(letter.toString(), 25, bold = true).apply {
            gravity = Gravity.CENTER
            background = surface(0xf5ffffff.toInt(), 14)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        private val answerText = label(answer, 29, bold = true).apply {
            maxLines = 2
            fitText(this, 20, 29)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        init {
            id = generateViewId()
            isFocusable = true; isFocusableInTouchMode = true; isClickable = true
            clipChildren = false
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), surface(color, 18, FOCUS, 4))
                addState(intArrayOf(android.R.attr.state_focused), surface(color, 18, FOCUS, 4))
                addState(intArrayOf(android.R.attr.state_selected), surface(color, 18, FOCUS, 2))
                addState(intArrayOf(), surface(color, 18))
            }
            place(this, badge, 39, 15, 40, 37)
            place(this, answerText, 100, 8, 150, 51)
            setOnClickListener { action() }
            accessibilityDelegate = object : AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = if (multiple) "android.widget.CheckBox" else "android.widget.RadioButton"
                    info.isCheckable = true; info.isChecked = isSelected
                }
            }
            update(false)
        }
        fun update(checked: Boolean) {
            isSelected = checked
            badge.text = if (checked) "✓" else letter.toString()
            contentDescription = "$letter $answer"
        }
    }

    private fun renderQuestion(card: FrameLayout, question: QuizQuestion, parent: Button) {
        val multiple = question.answers.size > 1
        place(card, label(question.prompt, 38, bold = true).apply {
            maxLines = 2; fitText(this, 23, 38)
        }, 33, 108, 540, 56)
        place(card, label(context.getString(if (multiple) R.string.quiz_pick_multiple else R.string.quiz_pick_one), 21).apply {
            maxLines = 1; fitText(this, 15, 21)
        }, 33, 165, 540, 31)
        val options = mutableListOf<AnswerTile>()
        val submit = button(context.getString(R.string.quiz_pick_before_submit)) {
            if (selected.isEmpty()) return@button
            if (selected == question.answers) { correct++; points += question.difficulty * 2 }
            index++
            selected.clear()
            render()
        }
        submit.isEnabled = false
        fun refreshSelection() {
            options.forEachIndexed { n, tile -> tile.update(n in selected) }
            submit.setText(if (selected.isEmpty()) R.string.quiz_pick_before_submit else R.string.quiz_submit)
            submit.isEnabled = selected.isNotEmpty()
        }
        question.choices.forEachIndexed { n, answer ->
            val tile = AnswerTile('A' + n, answer, ANSWER_COLORS[n % ANSWER_COLORS.size], multiple) {
                if (!multiple) {
                    if (n in selected) selected.clear() else { selected.clear(); selected.add(n) }
                } else if (!selected.add(n)) selected.remove(n)
                refreshSelection()
            }
            options.add(tile)
            place(card, tile, if (n % 2 == 0) 22 else 306, 208 + (n / 2) * 80, 270, 67)
        }
        place(card, submit, 17, 373, 563, 54)
        linkActions(submit, parent)
        options.forEachIndexed { n, option ->
            option.nextFocusLeftId = if (n % 2 == 1) options[n - 1].id else option.id
            option.nextFocusRightId = if (n % 2 == 0) options.getOrNull(n + 1)?.id ?: parent.id else parent.id
            option.nextFocusUpId = options.getOrNull(n - 2)?.id ?: option.id
            option.nextFocusDownId = options.getOrNull(n + 2)?.id ?: submit.id
            option.setOnFocusChangeListener { _, focused ->
                if (focused) { parent.nextFocusLeftId = option.id; parentReturn = option }
            }
        }
        submit.nextFocusUpId = options[options.lastIndex / 2 * 2].id
        focus(options.first())
    }

    private fun renderResult(card: FrameLayout, parent: Button) {
        val passed = correct >= 4
        place(card, label(context.getString(if (passed) R.string.quiz_pass else R.string.quiz_retry), 30, bold = true).apply {
            maxLines = 2; fitText(this, 23, 30)
        }, 33, 139, 530, 82)
        place(card, label(context.getString(R.string.quiz_score, correct, questions.size), 24), 33, 234, 530, 48)
        val next = button(context.getString(if (passed) R.string.quiz_continue else R.string.quiz_again)) {
            if (passed) {
                if (finished) return@button
                finished = true
                onPassed(points.coerceAtLeast(1))
            } else {
                questions = newRound(); index = 0; correct = 0; points = 0
                render()
            }
        }
        place(card, next, 17, 373, 563, 54)
        linkActions(next, parent)
        focus(next)
    }

    private fun linkActions(primary: View, parent: View) {
        primary.nextFocusLeftId = primary.id
        primary.nextFocusRightId = parent.id
        primary.nextFocusDownId = primary.id
        parentReturn = primary
        primary.setOnFocusChangeListener { _, focused ->
            if (focused) { parent.nextFocusLeftId = primary.id; parentReturn = primary }
        }
        // The parent entry lives in another FrameLayout. Resolve its return explicitly instead
        // of letting spatial focus search choose the bottom-right answer in that other column.
        parent.setOnKeyListener { _, code, event ->
            if (code != KeyEvent.KEYCODE_DPAD_LEFT) false else {
                if (event.action == KeyEvent.ACTION_DOWN) (parentReturn ?: primary).requestFocus()
                true
            }
        }
        parent.nextFocusLeftId = primary.id
        parent.nextFocusRightId = parent.id
        parent.nextFocusUpId = parent.id
        parent.nextFocusDownId = parent.id
    }

    fun showParentPin(onExit: () -> Unit) {
        showingPin = true
        pinExit = onExit
        renderParentPin()
    }

    private fun renderParentPin() {
        val page = canvas()
        val card = frame().apply { background = surface(0xfffaf6ff.toInt(), 28) }
        place(page, card, 260, 116, 440, 308)
        place(card, label(context.getString(R.string.kids_parent), 27, bold = true), 25, 22, 390, 40)
        val useArrows = AerialSettings.pinArrows
        val input = EditText(context).apply {
            setTextColor(INK)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, unit(24).toFloat())
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            isFocusable = !useArrows
            isSingleLine = true
        }
        place(card, input, 25, 74, 390, 48)
        var arrows = ""
        var arrowFocus: View? = null
        if (useArrows) {
            listOf("↑" to "U", "↓" to "D", "←" to "L", "→" to "R").forEachIndexed { n, (text, code) ->
                val key = button(text) { arrows += code; input.setText("•".repeat(arrows.length)) }
                if (n == 0) arrowFocus = key
                place(card, key, 25 + n * 100, 133, 90, 42)
            }
        }
        val submit = button(context.getString(R.string.quiz_submit)) {
            if (AerialSettings.pinMatches(if (useArrows) arrows else input.text.toString())) pinExit?.invoke()
            else { input.setText(""); arrows = "" }
        }
        place(card, submit, 25, 187, 390, 46)
        val back = button(context.getString(R.string.quiz_back_to_questions)) { render() }
        place(card, back, 25, 245, 390, 46)
        submit.nextFocusDownId = back.id; back.nextFocusUpId = submit.id
        focus(arrowFocus ?: if (useArrows) submit else input)
    }

    private companion object {
        val INK = 0xff30234f.toInt()
        val FOCUS = 0xff4c3b86.toInt()
        val PURPLE = 0xff7863b4.toInt()
        val ANSWER_COLORS = intArrayOf(0xffffb7c3.toInt(), 0xffffdc8e.toInt(), 0xffb7ecd5.toInt(), 0xffa6d5fb.toInt())
    }
}
