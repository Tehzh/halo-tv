package app.orchard.tv.lite

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.LinearLayout

/** Static pastel cards and the same D-pad grid navigation as the rest of Lite. */
internal class LiteQuizSheet(
    context: Context,
    private val parent: () -> Unit,
    private val finish: (Int) -> Unit,
) : Sheet(context, {}) {
    private val main = card(0xFFF8F5FD.toInt())
    private val help = card(0xFFF5EFFC.toInt())
    private var questions = Kids.questions()
    private var index = 0
    private var correct = 0
    private var reward = 0
    private val chosen = mutableSetOf<Int>()
    private val ink = 0xFF34274F.toInt()

    init {
        setBackgroundColor(0xFFE8DDF7.toInt())
        val layout = LinearLayout(context)
        addView(layout, FrameLayout.LayoutParams(Ui.pxi(872f), Ui.pxi(414f), Gravity.CENTER).apply {
            bottomMargin = Ui.pxi(22f)
        })
        layout.addView(main, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 2.05f))
        layout.addView(help, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
            leftMargin = Ui.pxi(14f)
        })
        addView(label(context.getString(R.string.quiz_remote_hint), 17f), FrameLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply { bottomMargin = Ui.pxi(18f) })
        render()
    }

    private fun card(color: Int) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(Ui.pxi(20f), Ui.pxi(20f), Ui.pxi(20f), Ui.pxi(18f))
        @Suppress("DEPRECATION")
        setBackgroundDrawable(GradientDrawable().apply { setColor(color); cornerRadius = Ui.px(26f) })
    }

    private fun label(text: String, size: Float, weight: Int = 500) =
        context.label(text, size, weight, ink).apply {
            setSingleLine(false); setHorizontallyScrolling(false); maxLines = 3; ellipsize = null
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

    private fun action(text: String, block: () -> Unit) =
        QuizButton(context, text, 0xFF6555A3.toInt(), 0xFFFFFFFF.toInt()) { false }.apply { action = block }

    private fun render(focus: Pair<Int, Int> = 0 to 0) {
        clearRows()
        main.removeAllViews()
        help.removeAllViews()
        main.addView(label(context.getString(R.string.quiz_title), 26f, 700))
        main.addView(label(context.getString(R.string.quiz_position, (index + 1).coerceAtMost(5), 5), 14f))
        val progress = LinearLayout(context)
        repeat(5) { step ->
            progress.addView(android.view.View(context).apply {
                @Suppress("DEPRECATION")
                setBackgroundDrawable(GradientDrawable().apply {
                    setColor(if (step <= index) 0xFF7868BA.toInt() else 0xFFD6CCE9.toInt())
                    cornerRadius = Ui.px(4f)
                })
            }, LinearLayout.LayoutParams(0, Ui.pxi(6f), 1f).apply { rightMargin = Ui.pxi(4f) })
        }
        main.addView(progress, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = Ui.pxi(10f); bottomMargin = Ui.pxi(10f)
        })
        main.addView(label(context.getString(R.string.quiz_goal), 15f))
        val parentButton = action(context.getString(R.string.kids_parent), parent)
        help.addView(label("✦", 50f, 700))
        help.addView(label(context.getString(R.string.quiz_rest_title), 23f, 700))
        help.addView(label(context.getString(R.string.quiz_rest_message), 16f).apply { maxLines = 10 },
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.pxi(10f) })
        help.addView(android.view.View(context), LinearLayout.LayoutParams(1, 0, 1f))
        help.addView(parentButton, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Ui.pxi(47f)))
        val question = questions.getOrNull(index)
        if (question == null) {
            main.addView(label(context.getString(R.string.quiz_score, correct, 5), 31f, 700))
            main.addView(label(context.getString(if (correct >= 4) R.string.quiz_pass else R.string.quiz_retry), 19f))
        } else {
            main.addView(label(question.prompt, 25f, 700))
            main.addView(label(context.getString(if (question.answers.size > 1) R.string.quiz_pick_multiple else R.string.quiz_pick_one), 14f))
            val colors = intArrayOf(0xFFFFBBC5.toInt(), 0xFFFFDF95.toInt(), 0xFFB8EDDA.toInt(), 0xFFB9DFFD.toInt())
            question.choices.indices.chunked(2).forEach { indices ->
                val line = LinearLayout(context)
                val buttons = indices.map { n ->
                    QuizButton(context, "${'A' + n}   ${question.choices[n]}", colors[n], ink) { n in chosen }.apply {
                        action = {
                            if (question.answers.size == 1) { chosen.clear(); chosen += n }
                            else if (!chosen.remove(n)) chosen += n
                            render(position)
                        }
                        line.addView(this, LinearLayout.LayoutParams(0, Ui.pxi(49f), 1f).apply {
                            topMargin = Ui.pxi(6f)
                            if (n % 2 == 1) leftMargin = Ui.pxi(8f)
                        })
                    }
                }
                main.addView(line)
                row(*buttons.toTypedArray())
            }
        }
        main.addView(android.view.View(context), LinearLayout.LayoutParams(1, 0, 1f))
        val next = action(context.getString(when {
            question == null && correct >= 4 -> R.string.quiz_continue
            question == null -> R.string.quiz_again
            chosen.isEmpty() -> R.string.quiz_pick_before_submit
            else -> R.string.quiz_submit
        })) {
            if (question == null) {
                if (correct >= 4) finish(reward)
                else { questions = Kids.questions(); index = 0; correct = 0; reward = 0; render() }
            } else if (chosen.isNotEmpty()) {
                if (chosen == question.answers) { correct++; reward += question.difficulty }
                chosen.clear()
                index++
                render()
            }
        }
        main.addView(next, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Ui.pxi(47f)).apply { topMargin = Ui.pxi(8f) })
        row(next, parentButton)
        focusFirst(focus.first, focus.second)
    }

    override fun onKey(e: KeyEvent) = if (e.keyCode == KeyEvent.KEYCODE_BACK) true else super.onKey(e)
}

private class QuizButton(
    context: Context, private val text: String, private val color: Int, private val ink: Int,
    private val selected: () -> Boolean,
) : Control(context) {
    override val focusScale = 1f
    private val label = Ui.text(18f, 700, ink)
    override fun onDraw(canvas: Canvas) {
        val radius = Ui.px(14f)
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        bg.color = color
        canvas.drawRoundRect(rect, radius, radius, bg)
        if (focusedLook || selected()) {
            bg.color = 0xFF423473.toInt()
            bg.style = android.graphics.Paint.Style.STROKE
            bg.strokeWidth = Ui.px(3f)
            rect.inset(bg.strokeWidth / 2f, bg.strokeWidth / 2f)
            canvas.drawRoundRect(rect, radius, radius, bg)
            bg.style = android.graphics.Paint.Style.FILL
        }
        val fitted = android.text.TextUtils.ellipsize(text, android.text.TextPaint(label), width - Ui.px(14f), android.text.TextUtils.TruncateAt.END).toString()
        canvas.drawText(fitted, (width - label.measureText(fitted)) / 2f, baseline(label, height / 2f), label)
    }
}
