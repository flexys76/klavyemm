package com.example.klavye

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        root.addView(TextView(this).apply {
            text = "flexys klavye"
            textSize = 28f
        })
        root.addView(TextView(this).apply {
            text = "\n1) Klavyeyi etkinleştir\n2) Klavyeyi seç\n3) Aşağıda dene\n"
            textSize = 16f
        })
        root.addView(Button(this).apply {
            text = "1) Etkinleştir"
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        })
        root.addView(Button(this).apply {
            text = "2) Klavyeyi seç"
            setOnClickListener {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }
        })
        root.addView(EditText(this).apply { hint = "3) Burada dene" })
        setContentView(root)
    }
}
