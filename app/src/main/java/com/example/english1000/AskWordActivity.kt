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
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
import java.text.Normalizer
import java.util.Locale

/**
 * A learner-initiated vocabulary lookup: RU word -> EN translation -> optional
 * examples, then wait for the next learner request. No automatic word timer.
 *
 * Speech input requires the on-device Android recognition service (Android 12+);
 * when unavailable, the on-screen Russian input is always usable.
 */
class AskWordActivity : Activity(), TextToSpeech.OnInitListener {
    private data class Entry(val id: Int, val en: String, val ru: String, val pron: String)
    private data class Example(val en: String, val ru: String)

    private val words = mutableListOf<Entry>()
    private val examples = mutableMapOf<String, List<Example>>()
    private val handler = Handler(Looper.getMainLooper())
    private val personalStore by lazy { PersonalWordStore(this) }
    private val prefs by lazy { getSharedPreferences("progress_v1", MODE_PRIVATE) }
    private var includeExamples = false
    private var listeningEnabled = false
    private var listening = false
    private var ttsReady = false
    private var speaking = false
    private var retryCount = 0
    private var speechGeneration = 0
    private var recognizer: SpeechRecognizer? = null
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
        val choice = prefs.getInt("ui_theme", 2)
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
        for (i in 0 until data.length()) {
            val item = data.getJSONObject(i)
            words.add(Entry(item.getInt("id"), item.getString("en"),
                item.getString("ru"), item.optString("pron")))
        }
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
            setStroke(px(1), Color.rgb(166, 143, 108))
        }

    private fun text(value: String, size: Float, color: Int = ink, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-light", if (bold) Typeface.NORMAL else Typeface.NORMAL)
            letterSpacing = 0.015f
        }

    private fun action(value: String, prominent: Boolean = false, onClick: () -> Unit) =
        Button(this).apply {
            text = value
            textSize = 15f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            letterSpacing = 0.015f
            isAllCaps = false
            setTextColor(if (prominent) opposite else ink)
            background = shape(if (prominent) ink else secondary)
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

        add(root, text(if (uiEnglish) "Translate a word" else "Перевести слово",
            29f, ink, true).apply { gravity = Gravity.START })
        add(root, text(
            if (uiEnglish) "Say a Russian word. I'll pronounce its English translation."
            else "Назови русское слово — услышишь английский перевод.",
            14f, muted).apply { gravity = Gravity.START }, 8)
        val settingsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        settingsRow.addView(text(if (includeExamples)
            (if (uiEnglish) "Examples: on" else "Примеры: включены")
            else (if (uiEnglish) "Examples: off" else "Примеры: выключены"),
            13f, muted).apply { gravity = Gravity.START },
            LinearLayout.LayoutParams(0, -2, 1f))
        settingsRow.addView(action(if (uiEnglish) "Change" else "Изменить") {
            ExampleModeSetup.show(this, if (uiEnglish) "Ask a word" else "Спроси слово") {
                includeExamples = it
                intent.putExtra(EXTRA_INCLUDE_EXAMPLES, it)
                recreate()
            }
        }, LinearLayout.LayoutParams(px(120), px(46)))
        add(root, settingsRow, 10)

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

        status = text(if (uiEnglish) "Ready for your next word" else
            "Жду следующее русское слово", 15f, muted).apply { gravity = Gravity.START }
        add(root, status, 14)

        wordInput = EditText(this).apply {
            hint = if (uiEnglish) "Type a Russian word" else "Напиши русское слово"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setTextColor(ink)
            setHintTextColor(muted)
        }
        add(root, wordInput, 12)
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        controls.addView(action(if (uiEnglish) "Translate" else "Перевести", true) {
            val query = wordInput.text.toString().trim()
            if (query.isNotBlank()) lookup(query)
        }, LinearLayout.LayoutParams(0, px(52), 1f).apply { rightMargin = px(5) })
        micButton = action(if (uiEnglish) "🎤 Speak" else "🎤 Сказать") {
            if (listeningEnabled) {
                listeningEnabled = false
                stopListening()
                status.text = if (uiEnglish) "Microphone off. Type a word or tap to listen."
                    else "Микрофон выключен. Введи слово или включи прослушивание."
                micButton.text = if (uiEnglish) "🎤 Speak" else "🎤 Сказать"
            } else beginListening()
        }
        controls.addView(micButton, LinearLayout.LayoutParams(0, px(52), 1f).apply {
            leftMargin = px(5)
        })
        add(root, controls, 10)

        add(root, action(if (uiEnglish) "My words and history" else "Мой словарь и история") {
            showPersonalWords()
        }, 10)
        examplesPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(12), px(12), px(12), px(12))
            background = shape(surface)
            visibility = View.GONE
        }
        add(root, examplesPanel, 14)
    }

    private fun normalized(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT).replace('ё', 'е'),
            Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .trim().replace(Regex("\\s+"), " ")

    private fun candidates(query: String): List<Entry> {
        val needle = normalized(query)
        if (needle.isEmpty()) return emptyList()
        return words.filter { word ->
            val equivalents = word.ru.split(';', ',', '/').map { normalized(it) }
            equivalents.any { it == needle }
        }.distinctBy { it.id }.take(16)
    }

    private fun lookup(query: String) {
        // Do not listen to the synthesizer or create overlapping speech requests.
        stopListening()
        speaking = false
        speechGeneration++
        val results = candidates(query)
        when {
            results.isEmpty() -> {
                russianWord.text = query
                englishWord.text = "—"
                pronunciation.text = ""
                examplesPanel.visibility = View.GONE
                status.text = if (uiEnglish)
                    "No verified translation in the offline dictionary. Try typing another word."
                else "В офлайн-словаре пока нет проверенного перевода. Назови другое слово."
                resumeRecognitionSoon()
            }
            results.size == 1 -> showTranslation(query, results.first())
            else -> {
                val choices = results.map { "${it.en} — ${it.ru}" }.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle(if (uiEnglish) "Choose the meaning" else "Уточни значение")
                    .setItems(choices) { _, pos -> showTranslation(query, results[pos]) }
                    .setNegativeButton(if (uiEnglish) "Cancel" else "Отмена") { _, _ ->
                        resumeRecognitionSoon()
                    }
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
            examples[word.en.lowercase(Locale.ROOT)].orEmpty().take(6)
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
                add(labels, text(phrase.ru, 15f, muted).apply {
                    gravity = Gravity.START
                }, 4)
                add(examplesPanel, labels, 8)
            }
        }
        if (!ttsReady) {
            status.text = if (uiEnglish) "Translation displayed. Audio is not ready."
                else "Перевод показан. Озвучивание недоступно."
            resumeRecognitionSoon()
            return
        }
        val token = ++speechGeneration
        speaking = true
        status.text = if (uiEnglish) "English translation" else "Английский перевод"
        fun finish() {
            if (speechGeneration != token) return
            speaking = false
            status.text = if (uiEnglish) "Ready for your next Russian word"
                else "Жду следующее русское слово"
            resumeRecognitionSoon()
        }
        fun playExample(i: Int) {
            if (speechGeneration != token) return
            if (i >= phrases.size) {
                finish()
                return
            }
            sayOffline(phrases[i].en, Locale.US) {
                if (speechGeneration != token) return@sayOffline
                sayOffline(phrases[i].ru, Locale("ru", "RU")) {
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

    private fun beginListening() {
        if (!isVoiceAvailable()) {
            status.text = if (uiEnglish)
                "On-device Russian recognition is unavailable. Use the text field."
                else "Автономное распознавание русской речи недоступно. Используй текстовое поле."
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 119)
            return
        }
        listeningEnabled = true
        micButton.text = if (uiEnglish) "Stop mic" else "Выключить микрофон"
        startListening()
    }

    private fun isVoiceAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(this)

    private fun startListening() {
        if (!listeningEnabled || listening || speaking || isFinishing || isDestroyed ||
            !isVoiceAvailable() || checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED) return
        recognizer?.destroy()
        recognizer = try {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
        } catch (_: Exception) {
            listeningEnabled = false
            micButton.text = if (uiEnglish) "🎤 Speak" else "🎤 Сказать"
            status.text = if (uiEnglish) "Microphone could not be started"
                else "Не удалось включить микрофон"
            return
        }
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                status.text = if (uiEnglish) "Listening for a Russian word…" else
                    "Слушаю следующее русское слово…"
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                if (!listening) return
                listening = false
                recognizer?.destroy()
                recognizer = null
                if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    // Listening remains active during silence; there is no
                    // word timer and no automatic substitution of a new word.
                    resumeRecognitionSoon()
                } else {
                    listeningEnabled = false
                    micButton.text = if (uiEnglish) "🎤 Speak" else "🎤 Сказать"
                    status.text = if (uiEnglish) "Offline recognition error: $error"
                        else "Ошибка автономного распознавания: $error"
                }
            }
            override fun onResults(results: Bundle?) {
                if (!listening) return
                listening = false
                recognizer?.destroy()
                recognizer = null
                val phrase = results?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )?.firstOrNull()?.trim().orEmpty()
                if (phrase.isBlank()) {
                    resumeRecognitionSoon()
                } else {
                    retryCount = 0
                    lookup(phrase)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val command = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        listening = true
        try {
            recognizer?.startListening(command)
        } catch (_: Exception) {
            listening = false
            recognizer?.destroy()
            recognizer = null
            listeningEnabled = false
            micButton.text = if (uiEnglish) "🎤 Speak" else "🎤 Сказать"
            status.text = if (uiEnglish) "Could not start offline recognition"
                else "Не удалось запустить автономное распознавание"
        }
    }

    private fun stopListening() {
        listening = false
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    private fun resumeRecognitionSoon() {
        if (!listeningEnabled || isFinishing || isDestroyed) return
        handler.postDelayed({
            if (!speaking && !listening) startListening()
        }, 700L)
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
        if (isVoiceAvailable()) beginListening()
        else if (::status.isInitialized) status.text = if (uiEnglish)
            "Enter a Russian word using the keyboard"
        else "Введи русское слово с клавиатуры"
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
        listeningEnabled = false
        stopListening()
        speechGeneration++
        if (::tts.isInitialized) tts.stop()
        speaking = false
        super.onPause()
    }

    override fun onDestroy() {
        listeningEnabled = false
        handler.removeCallbacksAndMessages(null)
        stopListening()
        if (::tts.isInitialized) tts.shutdown()
        personalStore.close()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_INCLUDE_EXAMPLES = "include_examples_for_ask_word"
    }
}
