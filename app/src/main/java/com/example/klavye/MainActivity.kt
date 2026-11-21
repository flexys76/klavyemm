package com.example.klavye

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("flexys", MODE_PRIVATE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        root.addView(TextView(this).apply {
            text = "flexys klavye"
            textSize = 28f
        })
        root.addView(TextView(this).apply {
            text = "\n1) Klavyeyi etkinleştir\n2) Klavyeyi seç\n3) GIF için Giphy anahtarını gir\n4) Aşağıda dene\n"
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
        root.addView(Button(this).apply {
            text = "Giphy anahtarı al"
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://developers.giphy.com/dashboard/")))
            }
        })
        val keyBox = EditText(this).apply {
            hint = "3) Giphy API anahtarı"
            setText(prefs.getString("giphy_key", ""))
            setSingleLine()
        }
        root.addView(keyBox)
        root.addView(Button(this).apply {
            text = "Anahtarı kaydet"
            setOnClickListener {
                prefs.edit().putString("giphy_key", keyBox.text.toString().trim()).apply()
                Toast.makeText(this@MainActivity, "Kaydedildi", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(EditText(this).apply { hint = "4) Burada dene" })
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
