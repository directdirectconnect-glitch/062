package com.example.english1000

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.text.InputType
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * A learner-initiated vocabulary lookup: RU word -> EN translation -> optional
 * examples, then wait for the next learner request. No automatic word timer.
 *
 * A microphone tap captures one utterance using the bundled Russian Vosk model.
 */
class AskWordActivity : Activity(), TextToSpeech.OnInitListener {
    private data class Entry(val id: Int, val en: String, val ru: String, val pron: String, val examplesEn: List<String>)
    private data class Example(val en: String, val ru: String)

    private val words = mutableMapOf<Int, Entry>()
    private lateinit var wordLookup: RussianWordLookup
    private val examples = mutableMapOf<String, List<Example>>()
    private val handler = Handler(Looper.getMainLooper())
    private val personalStore by lazy { PersonalWordStore(this) }
    private val prefs by lazy { getSharedPreferences("progress_v1", MODE_PRIVATE) }
    private var includeExamples = false
    private var listening = false
    private var ttsReady = false
    private var speechGeneration = 0
    private val vosk by lazy { OfflineVoskEngine(this) }
    private lateinit var tts: TextToSpeech
    private lateinit var status: TextView
    private lateinit var russianWord: TextView
    private lateinit var englishWord: TextView
    private lateinit var pronunciation: TextView
    private lateinit var examplesPanel: LinearLayout
    private lateinit var wordInput: EditText
    private lateinit var micButton: Button
    private var uiEnglish = false
    private var dark = false
    private val ink get() = if (dark) Color.rgb(241, 235, 225) else Color.rgb(27, 42, 57)
    private val muted get() = if (dark) Color.rgb(192, 184, 173) else Color.rgb(104, 116, 128)
    private val canvasColor get() = if (dark) Color.rgb(11, 24, 39) else Color.rgb(241, 245, 249)
    private val surface get() = if (dark) Color.rgb(26, 43, 61) else Color.WHITE
    private val secondary get() = if (dark) Color.rgb(38, 55, 73) else Color.rgb(226, 233, 239)
    private val opposite get() = Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uiEnglish = prefs.getString("ui_language", "ru") == "en"
        includeExamples = intent.getBooleanExtra(EXTRA_INCLUDE_EXAMPLES,
            ExampleModeSetup.selected(this))
        val choice = prefs.getInt("ui_theme", 1)
        dark = choice == 1 || (choice == 2 &&
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES)
        setTheme(if (dark) android.R.style.Theme_Material_NoActionBar
            else android.R.style.Theme_Material_Light_NoActionBar)
        loadDictionary()
        createUi()
        tts = TextToSpeech(this, this)
    }

    private fun loadDictionary() {
        val data = JSONArray(assets.open("words.json").bufferedReader().use { it.readText() })
        val labels = ArrayList<RussianWordLookup.Label>(data.length())
        for (i in 0 until data.length()) {
            val item = data.getJSONObject(i)
            val id = item.getInt("id")
            val translation = item.getString("ru")
            words[id] = Entry(id, item.getString("en"),
                translation, item.optString("pron"), item.dictionaryList("examplesEn"))
            labels.add(RussianWordLookup.Label(
                id, translation, item.optString("primaryRu"), item.dictionaryList("acceptedRu")
            ))
        }
        val forms = assets.open("russian_word_forms.tsv").bufferedReader()
            .useLines(RussianWordLookup::readForms)
        wordLookup = RussianWordLookup(labels, forms)
        val bank = JSONObject(assets.open("examples.json").bufferedReader().use {
            it.readText()
        }).getJSONObject("examples")
        val keys = bank.keys()
        while (keys.hasNext()) {
            val en = keys.next()
            val phrases = bank.getJSONArray(en)
            examples[en.lowercase(Locale.ROOT)] = (0 until phrases.length()).map { index ->
                val phrase = phrases.getJSONObject(index)
                Example(phrase.getString("en"), phrase.getString("ru"))
            }
        }
        // Scene examples cover useful physical surroundings where the regular
        // phrase bank has not yet been curated.
        val scenes = JSONObject(assets.open("scenes.json").bufferedReader().use {
            it.readText()
        }).getJSONArray("scenes")
        for (i in 0 until scenes.length()) {
            val scene = scenes.getJSONObject(i).getJSONArray("words")
            for (j in 0 until scene.length()) {
                val item = scene.getJSONObject(j)
                val word = item.getString("word").lowercase(Locale.ROOT)
                if (word in examples) continue
                val phrases = item.getJSONArray("examples")
                examples[word] = (0 until phrases.length()).take(2).map { k ->
                    val phrase = phrases.getJSONObject(k)
                    Example(phrase.getString("en"), phrase.getString("ru"))
                }
            }
        }
    }

    private fun px(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()

    private fun shape(color: Int, corners: Int = 12): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = px(if (corners == 12) 20 else corners).toFloat()
            setStroke(px(2), Color.rgb(255, 195, 62))
        }

    private fun text(value: String, size: Float, color: Int = ink, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-light", if (bold) Typeface.BOLD else Typeface.NORMAL)
            letterSpacing = 0.015f
        }

    private fun action(value: String, prominent: Boolean = false, onClick: () -> Unit) =
        Button(this).apply {
            text = value
            textSize = 15f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            letterSpacing = 0.015f
            isAllCaps = false
            setTextColor(if (prominent) Color.rgb(7, 28, 50) else if (dark) Color.WHITE else ink)
            background = shape(if (prominent) Color.rgb(255, 199, 67) else secondary)
            setOnClickListener { onClick() }
        }

    private fun add(column: LinearLayout, view: View, top: Int = 0) {
        column.addView(view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(top) })
    }

    private fun createUi() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(canvasColor)
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val wide = resources.configuration.screenWidthDp >= 600
            setPadding(px(if (wide) 28 else 16), px(24),
                px(if (wide) 28 else 16), px(24))
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
            setOnClickListener { goToHome() }
        }
        add(identity, text("English Words", 27f, ink, true))
        add(identity, text("LANGUAGE LEARNING", 10f, Color.rgb(255, 199, 67)), 3)
        header.addView(identity, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(action("⚙") { goToHome(settings = true) }.apply {
            contentDescription = if (uiEnglish) "Settings" else "Настройки"
        }, LinearLayout.LayoutParams(px(48), px(48)))
        add(root, header)
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(action("‹") { finish() },
            LinearLayout.LayoutParams(px(52), px(50)))
        titleRow.addView(text(if (uiEnglish) "Translate a word" else "Перевести слово",
            25f, ink, true).apply {
            gravity = Gravity.CENTER
            contentDescription = if (uiEnglish) "ASK A WORD" else "СПРОСИ СЛОВО"
        }, LinearLayout.LayoutParams(0, -2, 1f))
        add(root, titleRow, 14)
        add(root, text(
            if (uiEnglish) "Say a Russian word. I'll pronounce its English translation."
            else "Назови русское слово — услышишь английский перевод.",
            14f, muted).apply { gravity = Gravity.START }, 8)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(px(12), px(25), px(12), px(25))
            minimumHeight = px(190)
            background = shape(surface)
        }
        russianWord = text(if (uiEnglish) "Your Russian word" else "Твоё русское слово",
            22f, muted)
        add(card, russianWord)
        englishWord = text("—", 43f, ink, true)
        add(card, englishWord, 14)
        pronunciation = text("", 14f, muted)
        add(card, pronunciation, 5)
        add(root, card, 16)
        add(root, android.widget.CheckBox(this).apply {
            text = if (uiEnglish) "Usage examples" else "Примеры употребления"
            isChecked = includeExamples
            setTextColor(ink)
            setOnCheckedChangeListener { _, checked ->
                includeExamples = checked
                ExampleModeSetup.save(this@AskWordActivity, checked)
                if (!checked) examplesPanel.visibility = View.GONE
            }
        }, 10)

        status = text(if (uiEnglish) "Ready for your next word" else
            "Жду следующее русское слово", 15f, muted).apply { gravity = Gravity.START }
        add(root, status, 14)

        wordInput = EditText(this).apply {
            hint = if (uiEnglish) "Type a Russian word" else "Напиши русское слово"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setTextColor(ink)
            setHintTextColor(muted)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { _, action, _ ->
                if (action != android.view.inputmethod.EditorInfo.IME_ACTION_DONE) false
                else {
                    submitQuery()
                    true
                }
            }
            setOnTouchListener { _, event ->
                if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN && listening) {
                    stopListening()
                    status.text = if (uiEnglish) "Microphone off" else "Микрофон выключен"
                }
                false
            }
        }
        add(root, wordInput, 12)
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        controls.addView(action("→", true) { submitQuery() }.apply {
            contentDescription = if (uiEnglish) "Translate" else "Перевести"
        }, LinearLayout.LayoutParams(0, px(52), 1f).apply { rightMargin = px(5) })
        micButton = action(microphoneLabel()) {
            if (listening) {
                stopListening()
                status.text = if (uiEnglish) "Microphone off" else "Микрофон выключен"
            } else beginListening()
        }
        controls.addView(micButton, LinearLayout.LayoutParams(0, px(52), 1f).apply {
            leftMargin = px(5)
        })
        add(root, controls, 10)

        val historyButton = action(if (uiEnglish) "Translation history" else "История переводов") {
            showPersonalWords()
        }
        add(root, historyButton, 10)
        add(root, action(if (uiEnglish) "Dictionary and history" else "Мой словарь и история") {
            startActivity(Intent(this, VocabularyHistoryActivity::class.java))
        }, 8)
        examplesPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(12), px(12), px(12), px(12))
            background = shape(surface)
            visibility = View.GONE
        }
        add(root, examplesPanel, 14)

        // Fold expands the same translation screen rather than adding a
        // second pane. Update insets when resized without an activity restart.
        root.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
            val isWide = (right - left) / resources.displayMetrics.density >= 600f
            val horizontalPadding = px(if (isWide) 28 else 16)
            if (root.paddingLeft != horizontalPadding) {
                root.setPadding(horizontalPadding, root.paddingTop,
                    horizontalPadding, root.paddingBottom)
            }
        }
    }

    private fun candidates(query: String): List<Entry> =
        wordLookup.find(query).mapNotNull(words::get)

    private fun goToHome(settings: Boolean = false) {
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(if (settings) "open_settings" else "open_home", true)
        })
    }

    private fun submitQuery() {
        val query = wordInput.text.toString().trim()
        if (query.isNotEmpty()) lookup(query)
    }

    private fun lookup(query: String) {
        // Do not listen to the synthesizer or create overlapping speech requests.
        stopListening()
        speechGeneration++
        val results = candidates(query)
        when {
            results.isEmpty() -> {
                russianWord.text = query
                englishWord.text = "—"
                pronunciation.text = ""
                examplesPanel.visibility = View.GONE
                status.text = if (uiEnglish)
                    "No matching entry. Try the base form or enter the word manually."
                else "Слово не найдено. Попробуй начальную форму или введи слово вручную."
            }
            results.size == 1 -> showTranslation(query, results.first())
            else -> {
                val choices = results.map { "${it.en} — ${it.ru}" }.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle(if (uiEnglish) "Choose the meaning" else "Уточни значение")
                    .setItems(choices) { _, pos -> showTranslation(query, results[pos]) }
                    .setNegativeButton(if (uiEnglish) "Cancel" else "Отмена", null)
                    .show()
            }
        }
    }

    private fun showTranslation(query: String, word: Entry) {
        stopListening()
        russianWord.text = query
        englishWord.text = word.en
        pronunciation.text = word.pron
        wordInput.setText("")
        try {
            personalStore.addRequestedWord(word.id, word.en, word.ru, query)
        } catch (_: Exception) {
            status.text = if (uiEnglish) "Personal history could not be saved."
                else "Не удалось сохранить слово в личную историю."
        }
        val phrases = if (includeExamples)
            (word.examplesEn.map { Example(it, "") } + examples[word.en.lowercase(Locale.ROOT)].orEmpty())
                .distinctBy { it.en }.take(6)
        else emptyList()
        examplesPanel.removeAllViews()
        examplesPanel.visibility = if (phrases.isEmpty()) View.GONE else View.VISIBLE
        if (phrases.isNotEmpty()) {
            add(examplesPanel, text(if (uiEnglish) "USAGE EXAMPLES" else "ПРИМЕРЫ УПОТРЕБЛЕНИЯ",
                13f, muted, true).apply { gravity = Gravity.START })
            phrases.forEach { phrase ->
                val labels = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(px(10), px(9), px(10), px(9))
                    background = shape(canvasColor)
                }
                add(labels, text(phrase.en, 17f, ink, true).apply {
                    gravity = Gravity.START
                })
                if (phrase.ru.isNotBlank()) add(labels, text(phrase.ru, 15f, muted).apply {
                    gravity = Gravity.START
                }, 4)
                add(examplesPanel, labels, 8)
            }
        }
        if (!ttsReady) {
            status.text = if (uiEnglish) "Translation displayed. Audio is not ready."
                else "Перевод показан. Озвучивание недоступно."
            return
        }
        val token = ++speechGeneration
        status.text = if (uiEnglish) "English translation" else "Английский перевод"
        fun finish() {
            if (speechGeneration != token) return
                status.text = if (uiEnglish) "Ready for your next Russian word"
                else "Жду следующее русское слово"
        }
        fun playExample(i: Int) {
            if (speechGeneration != token) return
            if (i >= phrases.size) {
                finish()
                return
            }
            sayOffline(phrases[i].en, Locale.US) {
                if (speechGeneration != token) return@sayOffline
                if (phrases[i].ru.isBlank()) playExample(i + 1)
                else sayOffline(phrases[i].ru, Locale("ru", "RU")) {
                    if (speechGeneration == token) playExample(i + 1)
                }
            }
        }
        sayOffline(word.en, Locale.US) {
            if (speechGeneration != token) return@sayOffline
            playExample(0)
        }
    }

    private fun sayOffline(value: String, locale: Locale, done: () -> Unit) {
        if (!ttsReady) {
            done()
            return
        }
        val voice = tts.voices.orEmpty()
            .filter { !it.isNetworkConnectionRequired && it.locale.language == locale.language }
            .sortedByDescending { it.quality }
            .firstOrNull()
        if (voice == null || tts.setVoice(voice) != TextToSpeech.SUCCESS) {
            status.text = if (uiEnglish) "Offline voice unavailable: ${locale.language}"
                else "Нет установленного офлайн-голоса: ${locale.language}"
            // Skip the unavailable audio while keeping translations visible.
            done()
            return
        }
        tts.setSpeechRate(0.92f)
        val utterance = "ask_${System.nanoTime()}"
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {}
            override fun onDone(utteranceId: String) {
                if (utteranceId == utterance) handler.post { done() }
            }
            override fun onError(utteranceId: String) {
                if (utteranceId == utterance) handler.post {
                    status.text = if (uiEnglish) "Audio playback failed" else
                        "Не удалось воспроизвести озвучивание"
                    done()
                }
            }
        })
        if (tts.speak(value, TextToSpeech.QUEUE_FLUSH, null, utterance) == TextToSpeech.ERROR) {
            done()
        }
    }

    private fun microphoneLabel() = if (uiEnglish) "🎤 Speak" else "🎤 Сказать"

    private fun beginListening() {
        if (isFinishing || isDestroyed) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 119)
            return
        }
        speechGeneration++
        tts.stop()
        listening = true
        micButton.text = if (uiEnglish) "Stop mic" else "Выключить микрофон"
        vosk.listen("ru", 8_000L, onStatus = { status.text = it }) { result ->
            stopListening()
            status.text = when (result) {
                is VoiceRecognitionResult.Transcript -> {
                    wordInput.setText(result.text)
                    wordInput.setSelection(result.text.length)
                    if (uiEnglish) "Edit or tap the arrow to translate"
                    else "Исправьте слово или нажмите стрелку для перевода"
                }
                VoiceRecognitionResult.Silence ->
                    if (uiEnglish) "No speech heard. Retry or type."
                    else "Тишина. Повторите или введите слово."
                VoiceRecognitionResult.Unrecognized ->
                    if (uiEnglish) "Audio detected, speech unclear. Retry or type."
                    else "Звук есть, речь не распознана. Повторите или введите слово."
                VoiceRecognitionResult.MicrophoneError ->
                    if (uiEnglish) "Microphone unavailable. Retry or type."
                    else "Сбой микрофона. Попробуйте снова или введите слово."
                VoiceRecognitionResult.RecognitionError ->
                    if (uiEnglish) "Local recognition unavailable. Type a word."
                    else "Локальное распознавание недоступно. Введите слово."
            }
        }
    }

    private fun stopListening() {
        listening = false
        vosk.stop()
        micButton.text = microphoneLabel()
    }

    private fun showPersonalWords() {
        val entries = try { personalStore.recent(100) } catch (_: Exception) { emptyList() }
        val message = if (entries.isEmpty()) {
            if (uiEnglish) "No requested words yet." else "Пока нет запрошенных слов."
        } else entries.joinToString("\n") { "${it.english} — ${it.russian}  ·  ×${it.requests}" }
        AlertDialog.Builder(this)
            .setTitle(if (uiEnglish) "My word history" else "Мой словарь и история")
            .setMessage(message)
            .setPositiveButton(if (uiEnglish) "Close" else "Закрыть", null)
            .show()
    }

    override fun onInit(statusCode: Int) {
        ttsReady = statusCode == TextToSpeech.SUCCESS
        if (!ttsReady && ::status.isInitialized) {
            status.text = if (uiEnglish) "Offline pronunciation unavailable"
                else "Озвучивание недоступно: проверь голоса Android"
        }

    }

    override fun onRequestPermissionsResult(requestCode: Int,
        permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 119 &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            beginListening()
        } else if (requestCode == 119) {
            status.text = if (uiEnglish) "Microphone denied. Text input is available."
                else "Микрофон не разрешён. Можно вводить слово с клавиатуры."
        }
    }

    override fun onPause() {
        stopListening()
        speechGeneration++
        if (::tts.isInitialized) tts.stop()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        vosk.release()
        if (::tts.isInitialized) tts.shutdown()
        personalStore.close()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_INCLUDE_EXAMPLES = "include_examples_for_ask_word"
    }
}
