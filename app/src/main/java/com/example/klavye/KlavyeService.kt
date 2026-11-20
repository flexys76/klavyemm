package com.example.klavye

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

class KlavyeService : InputMethodService() {

    private enum class Page { LETTERS, SYMBOLS, EMOJI }

    private val tr = Locale("tr", "TR")
    private var shift = false
    private var page = Page.LETTERS
    private var container: LinearLayout? = null
    private val handler = Handler(Looper.getMainLooper())

    private val letters = listOf(
        "qwertyuıopğü",
        "asdfghjklşi",
        "zxcvbnmöç"
    )
    private val syms = listOf(
        "1234567890",
        "@#₺_&-+()/",
        "*\"':;!?%=."
    )

    private val emojis: List<String> = run {
        val s = "😀😁😂🤣😊😍😘😎🤔😅😭😡🥳😴🤯🥺😇🙄😉😜" +
                "👍👎👏🙏💪🤝👋👌💖💔🔥✨🎉💯⭐🌹" +
                "🐶🐱🦁🐻🐼🦊🍕🍔🍟☕🍰🍎" +
                "⚽🎮🎵🚗🚀🏠"
        val out = mutableListOf<String>()
        var i = 0
        while (i < s.length) {
            val n = Character.charCount(s.codePointAt(i))
            out += s.substring(i, i + n)
            i += n
        }
        out
    }

    override fun onCreateInputView(): View {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1B1B1F"))
            setPadding(6, 10, 6, 10)
        }
        container = c
        render()
        return c
    }

    private fun go(p: Page) {
        handler.post { page = p; shift = false; render() }
    }

    private fun render() {
        val c = container ?: return
        try {
            c.removeAllViews()
            if (page == Page.EMOJI) renderEmoji(c) else renderKeys(c)
        } catch (e: Exception) {
            page = Page.LETTERS
            shift = false
            c.removeAllViews()
            renderKeys(c)
        }
    }

    private fun renderKeys(c: LinearLayout) {
        val isSym = page == Page.SYMBOLS
        val rows = if (isSym) syms else letters

        c.addView(row(rows[0].map { keyText(it) }))
        c.addView(row(rows[1].map { keyText(it) }))

        val third = mutableListOf<View>()
        third += special(if (isSym) "" else "⇧", 1.5f, active = shift && !isSym) {
            if (!isSym) handler.post { shift = !shift; render() }
        }
        third += rows[2].map { keyText(it) }
        third += special("⌫", 1.5f, repeat = true) { backspace() }
        c.addView(row(third))

        c.addView(row(listOf(
            special(if (isSym) "ABC" else "?123", 1.5f) { go(if (isSym) Page.LETTERS else Page.SYMBOLS) },
            special("😊", 1f) { go(Page.EMOJI) },
            special(",", 1f) { type(",") },
            special("boşluk", 4f) { type(" ") },
            special(".", 1f) { type(".") },
            special("↵", 1.5f) { enter() }
        )))
    }

    private fun renderEmoji(c: LinearLayout) {
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(200))
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        emojis.chunked(8).forEach { chunk -> col.addView(row(chunk.map { emojiCell(it) })) }
        scroll.addView(col)
        c.addView(scroll)

        c.addView(row(listOf(
            special("ABC", 1.5f) { go(Page.LETTERS) },
            special("boşluk", 5f) { type(" ") },
            special("⌫", 1.5f, repeat = true) { backspace() },
            special("↵", 1.5f) { enter() }
        )))
    }

    private fun emojiCell(e: String): View = TextView(this).apply {
        text = e
        textSize = 26f
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
        setOnClickListener { type(e) }
    }

    private fun keyText(c: Char): View {
        val label = if (shift && page == Page.LETTERS) c.toString().uppercase(tr) else c.toString()
        return special(label, 1f) {
            type(label)
            if (shift) { shift = false; handler.post { render() } }
        }
    }

    private fun row(keys: List<View>) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        keys.forEach { addView(it) }
    }

    private fun special(
        label: String, weight: Float, active: Boolean = false,
        repeat: Boolean = false, action: () -> Unit
    ): View = TextView(this).apply {
        text = label
        textSize = 20f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, dp(52), weight).apply { setMargins(4, 5, 4, 5) }
        background = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            setColor(Color.parseColor(if (active) "#4F6BED" else "#2E2F36"))
        }
        val tick = object : Runnable {
            override fun run() { action(); handler.postDelayed(this, 60) }
        }
        setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    action()
                    if (repeat) handler.postDelayed(tick, 400)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(tick)
            }
            true
        }
    }

    private fun type(s: String) { currentInputConnection?.commitText(s, 1) }

    private fun backspace() {
        val ic = currentInputConnection ?: return
        if (ic.getSelectedText(0).isNullOrEmpty()) ic.deleteSurroundingText(1, 0)
        else ic.commitText("", 1)
    }

    private fun enter() {
        val ic = currentInputConnection ?: return
        val opts = currentInputEditorInfo?.imeOptions ?: 0
        val action = opts and EditorInfo.IME_MASK_ACTION
        val noEnterAction = (opts and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noEnterAction &&
            action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
