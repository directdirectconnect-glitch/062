package com.example.english1000

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray

/** P06: real offline dictionary, recent outcomes, errors and favorites.
 * The counts come from the installed dictionary and on-device learning events,
 * never from the numbers printed in the approved design reference.
 */
class VocabularyHistoryActivity : Activity() {
    private data class Word(val id: Int, val en: String, val ru: String)
    private val words = ArrayList<Word>()
    private val lastOutcomes = mutableMapOf<Int, String>()
    private val prefs by lazy { getSharedPreferences("vocabulary_history_ui_v1", MODE_PRIVATE) }
    private lateinit var matches: TextView
    private lateinit var summary: TextView
    private lateinit var entries: LinearLayout
    private lateinit var search: EditText
    private lateinit var previous: Button
    private lateinit var next: Button
    private lateinit var pageLabel: TextView
    private var filter = 0
    private var page = 0
    private var favorites = setOf<String>()
    private val navy = Color.rgb(7, 28, 50)
    private lateinit var tts: TextToSpeech
    private var speechReady = false
    private val gold = Color.rgb(255, 199, 67)
    private val pale = Color.rgb(245, 247, 250)

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun shape(fill: Int, border: Int = gold) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(13).toFloat()
        setStroke(dp(2), border)
    }
    private fun text(value: String, size: Float = 16f, color: Int = Color.WHITE,
                     bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER_VERTICAL
    }
    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value
        isAllCaps = false
        textSize = 13f
        setTextColor(gold)
        background = shape(navy)
        setOnClickListener { action() }
        minimumHeight = dp(48)
    }
    private fun add(parent: LinearLayout, view: android.view.View, top: Int = 0) {
        parent.addView(view, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(top)
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_Material_NoActionBar)
        tts = TextToSpeech(this) { speechReady = it == TextToSpeech.SUCCESS }
        val data = JSONArray(assets.open("words.json").bufferedReader().use { it.readText() })
        for (i in 0 until data.length()) {
            val obj = data.getJSONObject(i)
            words.add(Word(obj.getInt("id"), obj.getString("en"), obj.getString("ru")))
        }
        favorites = prefs.getStringSet("favorite_ids", emptySet())?.toSet() ?: emptySet()
        runCatching {
            LearningHistoryStore(this).use { store ->
                store.readableDatabase.rawQuery(
                    "SELECT word_id, outcome FROM learning_events WHERE id IN " +
                        "(SELECT MAX(id) FROM learning_events GROUP BY word_id)", null
                ).use { cursor ->
                    while (cursor.moveToNext()) lastOutcomes[cursor.getInt(0)] = cursor.getString(1)
                }
            }
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(navy)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(20), dp(13), dp(24))
        }
        scroll.addView(root)
        setContentView(scroll)
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            scroll.setOnApplyWindowInsetsListener { view, insets ->
                val safeArea = insets.getInsets(android.view.WindowInsets.Type.systemBars() or
                    android.view.WindowInsets.Type.displayCutout())
                view.setPadding(safeArea.left, safeArea.top, safeArea.right, safeArea.bottom)
                insets
            }
        }

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val identity = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = true
            setOnClickListener { openMain("open_home") }
        }
        add(identity, text("English Words", 25f, Color.WHITE, true))
        add(identity, text("LANGUAGE LEARNING", 10f, gold), 2)
        header.addView(identity, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(button("⚙") { openMain("open_settings") }.apply {
            contentDescription = "Настройки"
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        add(root, header)
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heading.addView(button("‹ Назад") { finish() },
            LinearLayout.LayoutParams(dp(105), dp(48)))
        heading.addView(text("Мой словарь и история", 20f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, -2, 1f))
        add(root, heading, 18)

        val filters = listOf("Мой словарь", "История", "Ошибки", "Избранное")
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        filters.forEachIndexed { index, title ->
            tabs.addView(button(title) {
                filter = index
                page = 0
                render()
            }, LinearLayout.LayoutParams(0, dp(55), 1f).apply {
                if (index > 0) leftMargin = dp(3)
            })
        }
        add(root, tabs, 12)
        summary = text("", 15f, Color.WHITE, true).apply {
            setPadding(dp(12), dp(14), dp(12), dp(14))
            background = shape(Color.rgb(9, 43, 73))
        }
        add(root, summary, 12)

        search = EditText(this).apply {
            hint = "Поиск слова..."
            setSingleLine(true)
            setTextColor(Color.BLACK)
            setHintTextColor(Color.DKGRAY)
            textSize = 16f
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = shape(pale)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    page = 0
                    render()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        add(root, search, 12)
        matches = text("", 12f, gold)
        add(root, matches, 10)
        entries = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        add(root, entries, 6)
        val paging = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        previous = button("‹") { if (page > 0) { page--; render() } }
        paging.addView(previous, LinearLayout.LayoutParams(dp(58), dp(52)))
        pageLabel = text("", 13f, Color.WHITE).apply { gravity = Gravity.CENTER }
        paging.addView(pageLabel, LinearLayout.LayoutParams(0, -2, 1f))
        next = button("›") { page++; render() }
        paging.addView(next, LinearLayout.LayoutParams(dp(58), dp(52)))
        add(root, paging, 12)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(button("Повторить ошибки") { openMain("open_check") },
            LinearLayout.LayoutParams(0, dp(58), 1f))
        actions.addView(button("Учить слова") { openMain("open_learn") },
            LinearLayout.LayoutParams(0, dp(58), 1f).apply { leftMargin = dp(4) })
        actions.addView(button("Результаты") {
            AlertDialog.Builder(this).setTitle("Результаты занятий")
                .setMessage(summary.text).setPositiveButton(android.R.string.ok, null).show()
        }, LinearLayout.LayoutParams(0, dp(58), 1f).apply { leftMargin = dp(4) })
        add(root, actions, 12)
        render()
    }

    private fun openMain(route: String) {
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(route, true)
        })
    }

    private fun pronounce(word: String) {
        if (!speechReady) return
        val voice = tts.voices.orEmpty().firstOrNull {
            it.locale.language == Locale.ENGLISH.language && !it.isNetworkConnectionRequired &&
                "notInstalled" !in it.features.orEmpty()
        }
        if (voice == null) {
            android.widget.Toast.makeText(this, "Установите английский офлайн-голос Android",
                android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        if (tts.setVoice(voice) == TextToSpeech.SUCCESS) {
            tts.speak(word, TextToSpeech.QUEUE_FLUSH, null, word)
        }
    }

    override fun onStop() {
        tts.stop()
        super.onStop()
    }

    override fun onDestroy() {
        tts.shutdown()
        super.onDestroy()
    }

    private fun render() {
        if (!::entries.isInitialized || !::search.isInitialized) return
        val query = search.text?.toString()?.trim()?.lowercase() ?: ""
        val filtered = words.filter { word ->
            val matching = query.isEmpty() || word.en.lowercase().contains(query) ||
                word.ru.lowercase().contains(query)
            matching && when (filter) {
                1 -> word.id in lastOutcomes
                2 -> lastOutcomes[word.id] == "incorrect"
                3 -> word.id.toString() in favorites
                else -> true
            }
        }
        val pageCount = ((filtered.size + 49) / 50).coerceAtLeast(1)
        page = page.coerceIn(0, pageCount - 1)
        val learned = lastOutcomes.values.count { it == "correct" }
        val errors = lastOutcomes.values.count { it == "incorrect" }
        val percent = if (words.isNotEmpty()) learned * 100 / words.size else 0
        summary.text = "Всего слов: ${words.size}   •   Изучено: $learned ($percent%)\n" +
            "Осталось: ${words.size - learned}   •   Ошибки: $errors   •   Избранное: ${favorites.size}"
        matches.text = "Показано слов: ${filtered.size}"
        entries.removeAllViews()
        for (word in filtered.drop(page * 50).take(50)) {
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(6), dp(6), dp(6))
                background = shape(Color.rgb(9, 43, 73), Color.rgb(202, 150, 46))
            }
            line.addView(text("${word.id}.  ${word.en}\n${word.ru}", 15f, Color.WHITE),
                LinearLayout.LayoutParams(0, -2, 1f))
            val result = when (lastOutcomes[word.id]) {
                "correct" -> "✓"
                "incorrect" -> "×"
                "skipped" -> "—"
                else -> ""
            }
            if (result.isNotEmpty()) line.addView(text(result, 16f, gold),
                LinearLayout.LayoutParams(dp(18), dp(42)))
            line.addView(button("🔊") { pronounce(word.en) }.apply {
                contentDescription = "Произнести ${word.en}"
            }, LinearLayout.LayoutParams(dp(44), dp(48)))
            val star = if (word.id.toString() in favorites) "★" else "☆"
            line.addView(button(star) {
                val id = word.id.toString()
                favorites = if (id in favorites) favorites - id else favorites + id
                prefs.edit().putStringSet("favorite_ids", favorites).apply()
                render()
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            add(entries, line, 5)
        }
        pageLabel.text = "Страница ${page + 1} из $pageCount"
        previous.isEnabled = page > 0
        next.isEnabled = page + 1 < pageCount
    }
}
