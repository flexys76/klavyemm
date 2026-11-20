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
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class KlavyeService : InputMethodService() {

    private val tr = Locale("tr", "TR")
    private var shift = false
    private var symbols = false
    private lateinit var container: LinearLayout
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

    override fun onCreateInputView(): View {
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1B1B1F"))
            setPadding(6, 10, 6, 10)
        }
        render()
        return container
    }

    private fun render() {
        container.removeAllViews()
        val rows = if (symbols) syms else letters

        container.addView(row(rows[0].map { keyText(it) }))
        container.addView(row(rows[1].map { keyText(it) }))

        val third = mutableListOf<View>()
        third += special(if (symbols) "" else "⇧", 1.5f, active = shift && !symbols) {
            if (!symbols) { shift = !shift; render() }
        }
        third += rows[2].map { keyText(it) }
        third += special("⌫", 1.5f, repeat = true) { backspace() }
        container.addView(row(third))

        container.addView(row(listOf(
            special(if (symbols) "ABC" else "?123", 1.5f) { symbols = !symbols; shift = false; render() },
            special(",", 1f) { type(",") },
            special("boşluk", 5f) { type(" ") },
            special(".", 1f) { type(".") },
            special("↵", 1.5f) { enter() }
        )))
    }

    private fun keyText(c: Char): View {
        val label = if (shift && !symbols) c.toString().uppercase(tr) else c.toString()
        return special(label, 1f) {
            type(label)
            if (shift) { shift = false; render() }
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
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
        val noEnterAction = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noEnterAction && action != null &&
            action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
