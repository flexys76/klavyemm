package com.example.klavye

import android.content.ClipDescription
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class KlavyeService : InputMethodService() {

    private enum class Page { LETTERS, SYMBOLS, EMOJI, GIF, CLIP }
    private class GifItem(val still: String, val gif: String)

    private val tr = Locale("tr", "TR")
    private var shift = false
    private var page = Page.LETTERS
    private var container: LinearLayout? = null
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newFixedThreadPool(4)

    private var gifItems: List<GifItem> = emptyList()
    private var gifStatus = ""
    private var gifToken = 0
    private val thumbs = ConcurrentHashMap<String, Bitmap>()

    private var clips = mutableListOf<String>()
    private var clipboard: ClipboardManager? = null

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

    private val alts = mapOf(
        'a' to "âäàáã", 'c' to "çć", 'e' to "éèêë", 'g' to "ğ", 'i' to "îíìï",
        'o' to "öôòó", 's' to "ş", 'u' to "üûùú", 'n' to "ñ", 'y' to "ýÿ",
        'z' to "ž", 'l' to "ł"
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

    override fun onCreate() {
        super.onCreate()
        clips = loadClips()
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard = cm
        cm.addPrimaryClipChangedListener { onClipChanged() }
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
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
        handler.post {
            page = p
            shift = false
            if (p == Page.CLIP) onClipChanged()
            render()
        }
    }

    private fun render() {
        val c = container ?: return
        try {
            c.removeAllViews()
            when (page) {
                Page.EMOJI, Page.GIF, Page.CLIP -> renderPanel(c)
                else -> renderKeys(c)
            }
        } catch (e: Exception) {
            page = Page.LETTERS
            shift = false
            c.removeAllViews()
            renderKeys(c)
        }
    }

    private fun refreshIfGif() {
        if (page == Page.GIF) render()
    }

    private fun renderKeys(c: LinearLayout) {
        val isSym = page == Page.SYMBOLS
        val rows = if (isSym) syms else letters

        c.addView(row(rows[0].map { letterKey(it) }))
        c.addView(row(rows[1].map { letterKey(it) }))

        val third = mutableListOf<View>()
        third += special(if (isSym) "" else "⇧", 1.5f, active = shift && !isSym) {
            if (!isSym) handler.post { shift = !shift; render() }
        }
        third += rows[2].map { letterKey(it) }
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

    private fun renderPanel(c: LinearLayout) {
        c.addView(row(listOf(tab("😊", Page.EMOJI), tab("GIF", Page.GIF), tab("📋", Page.CLIP))))
        val content = when (page) {
            Page.EMOJI -> emojiPanel()
            Page.GIF -> gifPanel()
            else -> clipPanel()
        }
        c.addView(content)
        c.addView(row(listOf(
            special("ABC", 1.5f) { go(Page.LETTERS) },
            special("boşluk", 5f) { type(" ") },
            special("⌫", 1.5f, repeat = true) { backspace() },
            special("↵", 1.5f) { enter() }
        )))
    }

    private fun tab(label: String, p: Page): View {
        val v = special(label, 1f, active = page == p) { go(p) }
        v.layoutParams.height = dp(40)
        return v
    }

    private fun msg(t: String): View = TextView(this).apply {
        text = t
        textSize = 15f
        setTextColor(Color.parseColor("#B8BAC4"))
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(24), dp(16), dp(24))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun pill(label: String, onClick: () -> Unit): View = TextView(this).apply {
        text = label
        textSize = 14f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setPadding(dp(12), 0, dp(12), 0)
        background = GradientDrawable().apply {
            cornerRadius = dp(17).toFloat()
            setColor(Color.parseColor("#2E2F36"))
        }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34))
            .apply { setMargins(4, 4, 4, 4) }
        setOnClickListener { onClick() }
    }

    // ---------- EMOJI ----------

    private fun emojiPanel(): View {
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(170))
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        emojis.chunked(8).forEach { chunk -> col.addView(row(chunk.map { emojiCell(it) })) }
        scroll.addView(col)
        return scroll
    }

    private fun emojiCell(e: String): View = TextView(this).apply {
        text = e
        textSize = 26f
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
        setOnClickListener { type(e) }
    }

    // ---------- PANO ----------

    private fun loadClips(): MutableList<String> =
        (getSharedPreferences("flexys", MODE_PRIVATE).getString("clips", "") ?: "")
            .split("\u0001").filter { it.isNotEmpty() }.toMutableList()

    private fun saveClips() {
        getSharedPreferences("flexys", MODE_PRIVATE).edit()
            .putString("clips", clips.joinToString("\u0001")).apply()
    }

    private fun onClipChanged() {
        val t = try {
            clipboard?.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
        } catch (e: Exception) {
            null
        }
        if (!t.isNullOrBlank() && t.length <= 1000) {
            clips.remove(t)
            clips.add(0, t)
            while (clips.size > 20) clips.removeAt(clips.size - 1)
            saveClips()
            if (page == Page.CLIP) handler.post { render() }
        }
    }

    private fun isPasswordField(): Boolean {
        val t = currentInputEditorInfo?.inputType ?: 0
        val cls = t and InputType.TYPE_MASK_CLASS
        val v = t and InputType.TYPE_MASK_VARIATION
        return (cls == InputType.TYPE_CLASS_TEXT && (
                v == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)) ||
                (cls == InputType.TYPE_CLASS_NUMBER && v == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
    }

    private fun clipPanel(): View {
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(170))
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (isPasswordField()) {
            col.addView(msg("Şifre alanında pano kapalı"))
        } else if (clips.isEmpty()) {
            col.addView(msg("Pano boş. Bir yazı kopyalayınca burada görünür."))
        } else {
            col.addView(pill("🗑 Temizle") { clips.clear(); saveClips(); render() })
            clips.forEach { t -> col.addView(clipCell(t)) }
        }
        scroll.addView(col)
        return scroll
    }

    private fun clipCell(t: String): View = TextView(this).apply {
        text = t
        textSize = 15f
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        setTextColor(Color.WHITE)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        background = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            setColor(Color.parseColor("#2E2F36"))
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(4, 4, 4, 4) }
        setOnClickListener { type(t) }
    }

    // ---------- GIF ----------

    private fun giphyKey(): String =
        (getSharedPreferences("flexys", MODE_PRIVATE).getString("giphy_key", "") ?: "").trim()

    private fun typedText(): String {
        if (isPasswordField()) return ""
        val before = currentInputConnection?.getTextBeforeCursor(40, 0)?.toString() ?: ""
        return before.substringAfterLast('\n').trim()
    }

    private fun httpGet(u: String): ByteArray {
        val conn = URL(u).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 10000
        try {
            if (conn.responseCode != 200) throw RuntimeException("HTTP " + conn.responseCode)
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private fun loadGifs(q: String) {
        val key = giphyKey()
        if (key.isEmpty()) {
            gifItems = emptyList()
            gifStatus = "API anahtarı yok. flexys klavye uygulamasını açıp anahtarı gir."
            refreshIfGif()
            return
        }
        val my = ++gifToken
        gifItems = emptyList()
        gifStatus = "Yükleniyor..."
        refreshIfGif()
        io.execute {
            try {
                val base = if (q.isBlank()) {
                    "https://api.giphy.com/v1/gifs/trending?"
                } else {
                    "https://api.giphy.com/v1/gifs/search?q=" +
                            URLEncoder.encode(q.take(50), "UTF-8") + "&lang=tr&"
                }
                val url = base + "api_key=" + URLEncoder.encode(key, "UTF-8") +
                        "&limit=24&rating=pg-13"
                val data = JSONObject(String(httpGet(url), Charsets.UTF_8)).getJSONArray("data")
                val list = (0 until data.length()).mapNotNull { i ->
                    val img = data.getJSONObject(i).getJSONObject("images")
                    val still = img.optJSONObject("fixed_width_small_still")?.optString("url")
                        ?: img.optJSONObject("fixed_width_still")?.optString("url")
                    val gif = img.optJSONObject("fixed_width")?.optString("url")
                        ?: img.optJSONObject("original")?.optString("url")
                    if (!still.isNullOrEmpty() && !gif.isNullOrEmpty()) GifItem(still, gif) else null
                }
                handler.post {
                    if (my == gifToken) {
                        gifItems = list
                        gifStatus = if (list.isEmpty()) "Sonuç yok" else ""
                        refreshIfGif()
                    }
                }
            } catch (e: Exception) {
                val m = e.message ?: ""
                val text = when {
                    m.contains("429") -> "Saatlik limit doldu (ücretsiz anahtar saatte 100 istek). Biraz bekle."
                    m.contains("401") || m.contains("403") -> "API anahtarı reddedildi. flexys klavye uygulamasında tekrar gir."
                    else -> "GIF yüklenemedi. İnternet bağlantını kontrol et."
                }
                handler.post {
                    if (my == gifToken) {
                        gifStatus = text
                        refreshIfGif()
                    }
                }
            }
        }
    }

    private fun gifPanel(): View {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(170))
        }
        val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        chipRow.addView(pill("✎ yazdığımı ara") { loadGifs(typedText()) })
        listOf("trend", "mutlu", "komik", "aşk", "üzgün", "evet", "hayır", "şaşkın", "dans", "tebrikler")
            .forEach { w -> chipRow.addView(pill(w) { loadGifs(if (w == "trend") "" else w) }) }
        chipRow.addView(TextView(this).apply {
            text = "Powered by GIPHY"
            textSize = 11f
            setTextColor(Color.parseColor("#80838F"))
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34))
        })
        wrap.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chipRow)
        })

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (gifStatus.isNotEmpty()) col.addView(msg(gifStatus))
        gifItems.chunked(3).forEach { chunk ->
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            chunk.forEach { r.addView(gifCell(it)) }
            repeat(3 - chunk.size) {
                r.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, dp(80), 1f) })
            }
            col.addView(r)
        }
        scroll.addView(col)
        wrap.addView(scroll)

        if (gifItems.isEmpty() && gifStatus.isEmpty()) handler.post { loadGifs("") }
        return wrap
    }

    private fun gifCell(item: GifItem): View {
        val iv = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor("#2E2F36"))
            layoutParams = LinearLayout.LayoutParams(0, dp(80), 1f).apply { setMargins(3, 3, 3, 3) }
            setOnClickListener { sendGif(item) }
        }
        val cached = thumbs[item.still]
        if (cached != null) {
            iv.setImageBitmap(cached)
        } else {
            io.execute {
                try {
                    val b = httpGet(item.still)
                    val bmp = BitmapFactory.decodeByteArray(b, 0, b.size)
                    if (bmp != null) {
                        if (thumbs.size > 80) thumbs.clear()
                        thumbs[item.still] = bmp
                        handler.post { iv.setImageBitmap(bmp) }
                    }
                } catch (e: Exception) {
                }
            }
        }
        return iv
    }

    private fun sendGif(item: GifItem) {
        io.execute {
            try {
                val dir = File(cacheDir, "gifs")
                dir.mkdirs()
                dir.listFiles()?.let { files ->
                    if (files.size > 20) {
                        files.sortedBy { it.lastModified() }.take(files.size - 20).forEach { it.delete() }
                    }
                }
                val name = "g" + Integer.toHexString(item.gif.hashCode()) + ".gif"
                val f = File(dir, name)
                if (!f.exists()) f.writeBytes(httpGet(item.gif))
                handler.post { commitGif(name, item.gif) }
            } catch (e: Exception) {
                handler.post {
                    gifStatus = "GIF indirilemedi"
                    refreshIfGif()
                }
            }
        }
    }

    private fun commitGif(name: String, link: String) {
        val ic = currentInputConnection ?: return
        var ok = false
        if (Build.VERSION.SDK_INT >= 25) {
            try {
                val uri = Uri.parse("content://com.example.klavye.gifs/" + name)
                val info = InputContentInfo(
                    uri, ClipDescription("gif", arrayOf("image/gif")), Uri.parse(link)
                )
                ok = ic.commitContent(info, InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)
            } catch (e: Exception) {
                ok = false
            }
        }
        if (!ok) type(link)
    }

    // ---------- TUSLAR ----------

    private fun commitLetter(s: String) {
        type(s)
        if (shift) { shift = false; handler.post { render() } }
    }

    private fun letterKey(c: Char): View {
        val upper = shift && page == Page.LETTERS
        val label = if (upper) c.toString().uppercase(tr) else c.toString()
        val options = if (page == Page.LETTERS) (alts[c] ?: "") else ""
        if (options.isEmpty()) return special(label, 1f) { commitLetter(label) }

        val choices = options.map { if (upper) it.toString().uppercase(tr) else it.toString() }
        val key = special(label, 1f) { }
        var popup: PopupWindow? = null
        var longPressed = false
        var selected = 0
        var popupLeft = 0
        val cells = mutableListOf<TextView>()

        fun highlight() {
            cells.forEachIndexed { i, t ->
                t.setBackgroundColor(if (i == selected) Color.parseColor("#4F6BED") else Color.TRANSPARENT)
            }
        }

        val show = Runnable {
            longPressed = true
            key.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(4), dp(4), dp(4), dp(4))
                background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(Color.parseColor("#3B3D47"))
                }
            }
            cells.clear()
            choices.forEach { ch ->
                val t = TextView(this).apply {
                    text = ch
                    textSize = 22f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(dp(44), dp(48))
        
