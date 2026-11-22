package app.orchard.tv.ui.home

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.FocusFinder
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import app.orchard.tv.R
import app.orchard.tv.settings.AerialSettings
import app.orchard.tv.settings.Restricted
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
    private var inputEnabled = true
    private var resumeFocus: View? = null
    private var pressedTarget: View? = null
    private var pressedCode = KeyEvent.KEYCODE_UNKNOWN
    private var pinEntered = ""
    private var pinError = false
    private var pinFeedback: (() -> Unit)? = null
    private val resizePage = Runnable {
        val previous = (findFocus() ?: resumeFocus)?.tag
        if (showingPin) renderParentPin() else render()
        previous?.let { tag -> findViewWithTag<View>(tag)?.let(::focus) }
    }

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
        removeCallbacks(resizePage)
        post(resizePage)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) cancelPress()
        if (hasWindowFocus && inputEnabled && !hasFocus()) initialFocus?.let(::focus)
    }

    /** Compose's PIN must own focus exclusively while it covers this native view. */
    fun setInteractionEnabled(enabled: Boolean) {
        if (inputEnabled == enabled) return
        inputEnabled = enabled
        cancelPress()
        if (!enabled) {
            resumeFocus = findFocus()
            descendantFocusability = FOCUS_BLOCK_DESCENDANTS
            clearFocus()
        } else {
            descendantFocusability = FOCUS_AFTER_DESCENDANTS
            (resumeFocus?.takeIf { it.isAttachedToWindow } ?: initialFocus)?.let(::focus)
            resumeFocus = null
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(resizePage)
        cancelPress()
        super.onDetachedFromWindow()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        if (!inputEnabled) true else super.dispatchTouchEvent(event)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!inputEnabled) return false
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_BACK) {
            cancelPress()
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled && showingPin) returnToQuestions()
            return true
        }
        if (showingPin) {
            val symbol = if (AerialSettings.pinArrows) PIN_ARROWS[code]
                else if (code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) '0' + (code - KeyEvent.KEYCODE_0) else null
            if (symbol != null || code == KeyEvent.KEYCODE_DEL) {
                cancelPress()
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    if (symbol != null) typePin(symbol) else erasePin()
                }
                return true
            }
        }
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A) {
            // Never let the release of an old press click a newly rendered question or PIN key.
            when (event.action) {
                KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                    cancelPress()
                    pressedTarget = findFocus()?.takeIf { it.isEnabled && it.isClickable }
                    pressedCode = code
                    pressedTarget?.isPressed = true
                }
                KeyEvent.ACTION_UP -> {
                    val target = pressedTarget
                    val sameKey = pressedCode == code
                    cancelPress()
                    if (!event.isCanceled && sameKey && target != null && target === findFocus() &&
                        target.isEnabled && target.isAttachedToWindow) target.performClick()
                }
            }
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN) cancelPress()
        val direction = when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT -> FOCUS_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> FOCUS_RIGHT
            KeyEvent.KEYCODE_DPAD_UP -> FOCUS_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> FOCUS_DOWN
            else -> null
        }
        if (direction != null) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                (FocusFinder.getInstance().findNextFocus(this, findFocus(), direction) ?: initialFocus)?.requestFocus()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun cancelPress() {
        pressedTarget?.isPressed = false
        pressedTarget = null
        pressedCode = KeyEvent.KEYCODE_UNKNOWN
    }

    private fun unit(value: Int) = (value * scale).roundToInt().coerceAtLeast(1)

    private fun newRound(): List<QuizQuestion> {
        val prefs = context.getSharedPreferences("halo_quiz_history", Context.MODE_PRIVATE)
        val seen = prefs.getStringSet("seen", emptySet()).orEmpty()
        return QuizBank.round(seen, stage = Restricted.quizStage, subject = Restricted.quizSubject).also { round ->
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
        setTextColor(ColorStateList(arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf(android.R.attr.state_focused), intArrayOf()), intArrayOf(INK, INK, WHITE)))
        gravity = Gravity.CENTER
        minimumHeight = 0; minHeight = 0; minimumWidth = 0; minWidth = 0
        includeFontPadding = false
        setPadding(unit(12), 0, unit(12), 0)
        stateListAnimator = null
        backgroundTintList = null
        background = StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), surface(0xff9d8ed5.toInt(), 18))
            addState(intArrayOf(android.R.attr.state_pressed), surface(FOCUS_FILL, 18, FOCUS, 4))
            addState(intArrayOf(android.R.attr.state_focused), surface(FOCUS_FILL, 18, FOCUS, 4))
            addState(intArrayOf(), surface(PURPLE, 18))
        }
        setOnClickListener { if (inputEnabled && isAttachedToWindow) action() }
    }

    private fun focus(view: View) {
        initialFocus = view
        view.post {
            if (inputEnabled && initialFocus === view && view.isAttachedToWindow && view.isEnabled) view.requestFocus()
        }
    }

    private fun canvas(): FrameLayout {
        cancelPress()
        initialFocus = null
        removeAllViews()
        return frame().also { addView(it, LayoutParams(unit(960), unit(540), Gravity.CENTER)) }
    }

    private fun render() {
        showingPin = false
        pinFeedback = null
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
        val parent = button(context.getString(R.string.kids_parent)) { onParent(this) }.apply { tag = "parent" }
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
            setOnClickListener { if (inputEnabled && isAttachedToWindow) action() }
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
        submit.tag = "submit"
        fun refreshSelection() {
            options.forEachIndexed { n, tile -> tile.update(n in selected) }
            submit.setText(if (selected.isEmpty()) R.string.quiz_pick_before_submit else R.string.quiz_submit)
            submit.isEnabled = selected.isNotEmpty()
            options.forEachIndexed { n, tile ->
                tile.nextFocusDownId = options.getOrNull(n + 2)?.id ?: if (submit.isEnabled) submit.id else parent.id
            }
        }
        question.choices.forEachIndexed { n, answer ->
            val tile = AnswerTile('A' + n, answer, ANSWER_COLORS[n % ANSWER_COLORS.size], multiple) {
                if (!multiple) {
                    if (n in selected) selected.clear() else { selected.clear(); selected.add(n) }
                } else if (!selected.add(n)) selected.remove(n)
                refreshSelection()
            }
            options.add(tile)
            tile.tag = "answer-$n"
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
                if (focused) parent.nextFocusLeftId = option.id
            }
        }
        submit.nextFocusUpId = options[options.lastIndex / 2 * 2].id
        refreshSelection()
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
        next.tag = "submit"
        place(card, next, 17, 373, 563, 54)
        linkActions(next, parent)
        focus(next)
    }

    private fun linkActions(primary: View, parent: View) {
        primary.nextFocusLeftId = primary.id
        primary.nextFocusRightId = parent.id
        primary.nextFocusDownId = primary.id
        primary.setOnFocusChangeListener { _, focused ->
            if (focused) parent.nextFocusLeftId = primary.id
        }
        // Return to the last focused action/answer across the two card containers.
        parent.nextFocusLeftId = primary.id
        parent.nextFocusRightId = parent.id
        parent.nextFocusUpId = parent.id
        parent.nextFocusDownId = parent.id
    }

    fun showParentPin(onExit: () -> Unit) {
        if (showingPin) return
        showingPin = true
        pinExit = onExit
        pinEntered = ""
        pinError = false
        renderParentPin()
    }

    private fun returnToQuestions() {
        pinEntered = ""
        pinExit = null
        render()
        findViewWithTag<View>("parent")?.let(::focus)
    }

    private fun typePin(symbol: Char) {
        if (!showingPin || pinEntered.length >= 4) return
        pinError = false
        pinEntered += symbol
        if (pinEntered.length == 4) {
            val right = AerialSettings.pinMatches(pinEntered)
            pinEntered = ""
            if (right) {
                val exit = pinExit
                pinExit = null
                setInteractionEnabled(false)
                exit?.invoke()
                return
            }
            pinError = true
        }
        pinFeedback?.invoke()
    }

    private fun erasePin() {
        pinEntered = pinEntered.dropLast(1)
        pinError = false
        pinFeedback?.invoke()
    }

    private fun renderParentPin() {
        val page = canvas()
        val card = frame().apply { background = surface(0xfffaf6ff.toInt(), 28) }
        place(page, card, 260, 34, 440, 472)
        place(card, label(context.getString(R.string.pin_enter), 27, bold = true), 25, 16, 390, 40)
        val useArrows = AerialSettings.pinArrows
        val hint = label("", 16).apply { gravity = Gravity.CENTER; maxLines = 2 }
        place(card, hint, 25, 60, 390, 46)
        val dots = label("", 28, bold = true).apply { gravity = Gravity.CENTER }
        place(card, dots, 25, 109, 390, 38)
        pinFeedback = {
            hint.setText(if (pinError) R.string.pin_wrong else if (useArrows) R.string.pin_arrows_hint else R.string.pin_enter_hint)
            hint.setTextColor(if (pinError) 0xffa51f3d.toInt() else INK)
            dots.text = (0 until 4).joinToString("  ") { if (it < pinEntered.length) "●" else "○" }
        }
        pinFeedback?.invoke()
        val back = button(context.getString(R.string.quiz_back_to_questions), ::returnToQuestions).apply { tag = "pin-back" }
        place(card, back, 25, 410, 390, 46)
        if (!useArrows) {
            val keys = mutableListOf<Button>()
            listOf("123", "456", "789", " 0‹").forEachIndexed { row, chars ->
                chars.forEachIndexed { col, char ->
                    if (char != ' ') {
                        val key = button(char.toString()) { if (char == '‹') erasePin() else typePin(char) }
                        key.tag = "pin-$char"
                        place(card, key, 25 + col * 133, 155 + row * 59, 124, 49)
                        keys += key
                    }
                }
            }
            keys.forEachIndexed { n, key ->
                val lastRow = n >= 9
                key.nextFocusLeftId = if ((n < 9 && n % 3 > 0) || n == 10) keys[n - 1].id else key.id
                key.nextFocusRightId = if ((n < 9 && n % 3 < 2) || n == 9) keys[n + 1].id else key.id
                key.nextFocusUpId = if (lastRow) keys[n - 2].id else keys.getOrNull(n - 3)?.id ?: key.id
                key.nextFocusDownId = when {
                    n < 6 -> keys[n + 3].id
                    n < 9 -> keys[if (n == 8) 10 else 9].id
                    else -> back.id
                }
            }
            back.nextFocusUpId = keys[9].id
            back.nextFocusLeftId = back.id; back.nextFocusRightId = back.id; back.nextFocusDownId = back.id
            focus(keys.first())
        } else {
            // Arrow PINs are typed directly, matching the normal parental dialog; no IME is needed.
            focus(back)
        }
    }

    private companion object {
        val INK = 0xff30234f.toInt()
        val FOCUS = 0xff4c3b86.toInt()
        val PURPLE = 0xff7863b4.toInt()
        val WHITE = 0xffffffff.toInt()
        val FOCUS_FILL = 0xffffe6a6.toInt()
        val PIN_ARROWS = mapOf(KeyEvent.KEYCODE_DPAD_UP to 'U', KeyEvent.KEYCODE_DPAD_DOWN to 'D',
            KeyEvent.KEYCODE_DPAD_LEFT to 'L', KeyEvent.KEYCODE_DPAD_RIGHT to 'R')
        val ANSWER_COLORS = intArrayOf(0xffffb7c3.toInt(), 0xffffdc8e.toInt(), 0xffb7ecd5.toInt(), 0xffa6d5fb.toInt())
    }
}
