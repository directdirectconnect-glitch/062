package com.example.english1000

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import java.text.Normalizer
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    data class Word(val id: Int, val en: String, val ru: String, val pron: String)
    private val words = mutableListOf<Word>()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var tts: TextToSpeech
    private var ttsReady = false
    private var englishOfflineVoices: List<Voice> = emptyList()
    private var russianOfflineVoices: List<Voice> = emptyList()
    private val voiceRates = floatArrayOf(0.80f, 0.90f, 1.00f, 1.10f, 1.20f)
    private lateinit var englishVoiceSpinner: Spinner
    private lateinit var russianVoiceSpinner: Spinner
    private lateinit var speedSpinner: Spinner
    private lateinit var voiceInfo: TextView
    private lateinit var deviceInfo: TextView
    private var onDeviceRecognitionSupported = false
    private var recognizer: SpeechRecognizer? = null
    private var direction = 0
    private var method = 0
    private var questionDirection = 0
    private var sessionSize = 50
    private var selectionMode = 0
    private var index = 0
    private var running = false
    private var answering = false
    private val prefs by lazy { getSharedPreferences("progress_v1", MODE_PRIVATE) }
    private var reviewIds: List<Int> = emptyList()
    private var current: Word? = null

    private val navy = Color.rgb(29, 35, 43)
    private val muted = Color.rgb(105, 115, 126)
    private val blue = Color.rgb(42, 81, 110)
    private val bg = Color.rgb(246, 247, 249)
    private val white = Color.WHITE
    private val red = Color.rgb(170, 63, 70)
    private lateinit var menu: LinearLayout
    private lateinit var menuButton: Button
    private lateinit var prompt: TextView
    private lateinit var pronunciation: TextView
    private lateinit var counter: TextView
    private lateinit var status: TextView
    private lateinit var stats: TextView
    private lateinit var progress: ProgressBar
    private lateinit var listen: Button
    private lateinit var correct: Button
    private lateinit var incorrect: Button
    private lateinit var repeat: Button
    private lateinit var skip: Button
    private lateinit var dirSpinner: Spinner
    private lateinit var methodSpinner: Spinner
    private lateinit var blockSpinner: Spinner
    private lateinit var selectionSpinner: Spinner
    private lateinit var formsCheck: CheckBox
    private val irregularForms = mapOf(
        "go" to "go · goes · went · gone · going",
        "begin" to "begin · begins · began · begun · beginning",
        "break" to "break · breaks · broke · broken · breaking",
        "bring" to "bring · brings · brought · bringing",
        "buy" to "buy · buys · bought · buying",
        "choose" to "choose · chooses · chose · chosen · choosing",
        "drink" to "drink · drinks · drank · drunk · drinking",
        "drive" to "drive · drives · drove · driven · driving",
        "eat" to "eat · eats · ate · eaten · eating",
        "fall" to "fall · falls · fell · fallen · falling",
        "feel" to "feel · feels · felt · feeling",
        "find" to "find · finds · found · finding",
        "give" to "give · gives · gave · given · giving",
        "hear" to "hear · hears · heard · hearing",
        "keep" to "keep · keeps · kept · keeping",
        "know" to "know · knows · knew · known · knowing",
        "leave" to "leave · leaves · left · leaving",
        "lose" to "lose · loses · lost · losing",
        "meet" to "meet · meets · met · meeting",
        "pay" to "pay · pays · paid · paying",
        "read" to "read · reads · read · reading",
        "run" to "run · runs · ran · running",
        "say" to "say · says · said · saying",
        "sell" to "sell · sells · sold · selling",
        "send" to "send · sends · sent · sending",
        "sit" to "sit · sits · sat · sitting",
        "sleep" to "sleep · sleeps · slept · sleeping",
        "speak" to "speak · speaks · spoke · spoken · speaking",
        "stand" to "stand · stands · stood · standing",
        "swim" to "swim · swims · swam · swum · swimming",
        "teach" to "teach · teaches · taught · teaching",
        "tell" to "tell · tells · told · telling",
        "think" to "think · thinks · thought · thinking",
        "understand" to "understand · understands · understood · understanding",
        "wear" to "wear · wears · wore · worn · wearing",
        "do" to "do · does · did · done · doing",
        "see" to "see · sees · saw · seen · seeing",
        "take" to "take · takes · took · taken · taking",
        "write" to "write · writes · wrote · written · writing",
        "have" to "have · has · had · having",
        "be" to "be · am · is · are · was · were · been · being",
        "come" to "come · comes · came · coming",
        "get" to "get · gets · got · gotten · getting",
        "make" to "make · makes · made · making"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val json = JSONArray(assets.open("words.json").bufferedReader().use { it.readText() })
        for (i in 0 until json.length()) {
            val w = json.getJSONObject(i)
            words.add(Word(w.getInt("id"), w.getString("en"), w.getString("ru"), w.optString("pron")))
        }
        onDeviceRecognitionSupported =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        tts = TextToSpeech(this, this)
        buildUi()
        restoreSession(savedInstanceState)
        refresh()
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()
    private fun rounded(color: Int, radius: Int = 12): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun text(value: String, size: Float, color: Int = navy, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            gravity = Gravity.CENTER
            if (bold) setTypeface(null, Typeface.BOLD)
        }
    private fun button(value: String, color: Int = blue, ink: Int = white, action: () -> Unit): Button =
        Button(this).apply {
            text = value; textSize = 15f; isAllCaps = false
            setTextColor(ink); background = rounded(color, 10)
            setOnClickListener { action() }
        }
    private fun add(parent: LinearLayout, view: View, height: Int = -2, top: Int = 0) {
        parent.addView(view, LinearLayout.LayoutParams(-1, if (height == -2) -2 else dp(height)).apply {
            topMargin = dp(top)
        })
    }
    private fun spinner(options: List<String>): Spinner = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, options)
        minimumHeight = dp(44)
    }
    private fun setting(parent: LinearLayout, title: String, spinner: Spinner) {
        add(parent, text(title, 14f, muted).apply { gravity = Gravity.START }, top = 10)
        add(parent, spinner)
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply { setFillViewport(true); setBackgroundColor(bg) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val wide = resources.configuration.screenWidthDp >= 600
            setPadding(dp(if (wide) 24 else 14), dp(16), dp(if (wide) 24 else 14), dp(24))
        }
        scroll.addView(root)
        setContentView(scroll)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(navy, 12)
            setPadding(dp(14), dp(10), dp(12), dp(10))
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        add(brand, text("ENGLISH WORDS", 21f, white, true).apply { gravity = Gravity.START })
        add(brand, text("ОФЛАЙН-СЛОВАРЬ · ${words.size} СЛОВ", 11f, Color.rgb(190, 200, 211)).apply { gravity = Gravity.START }, top = 3)
        header.addView(brand, LinearLayout.LayoutParams(0, dp(60), 1f))
        menuButton = button("Меню", Color.rgb(235, 238, 242), navy) { toggleMenu() }
        header.addView(menuButton, LinearLayout.LayoutParams(dp(if (resources.configuration.screenWidthDp >= 600) 112 else 92), dp(48)))
        add(root, header)

        menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(18))
            background = rounded(white, 12)
            visibility = View.GONE
        }
        add(menu, text("НАСТРОЙКИ", 16f, navy, true))
        deviceInfo = text("", 13f, muted).apply { gravity = Gravity.START }
        add(menu, deviceInfo, top = 10)
        updateDeviceCapabilities()
        dirSpinner = spinner(listOf("English → Русский", "Русский → English", "Смешанный"))
        methodSpinner = spinner(listOf("Голосовая проверка", "Кнопки: знаю / не знаю"))
        blockSpinner = spinner(listOf("10 слов", "20 слов", "30 слов", "50 слов")).apply { setSelection(3) }
        selectionSpinner = spinner(listOf("Из всего словаря", "Только ошибки", "Ещё не проверенные"))
        setting(menu, "Направление перевода", dirSpinner)
        setting(menu, "Способ ответа", methodSpinner)
        setting(menu, "Размер блока", blockSpinner)
        setting(menu, "Какие слова повторять", selectionSpinner)
        formsCheck = CheckBox(this).apply { text = "Показывать формы глагола после ответа"; isChecked = true }
        add(menu, formsCheck, top = 10)

        add(menu, text("ОЗВУЧИВАНИЕ БЕЗ ИНТЕРНЕТА", 16f, navy, true), top = 16)
        englishVoiceSpinner = spinner(listOf("Загрузка голосов..."))
        russianVoiceSpinner = spinner(listOf("Загрузка голосов..."))
        speedSpinner = spinner(listOf("0.80×", "0.90×", "1.00×", "1.10×", "1.20×"))
        speedSpinner.setSelection(prefs.getInt("speech_speed", 2).coerceIn(0, voiceRates.lastIndex))
        speedSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit().putInt("speech_speed", position).apply()
            }
        }
        setting(menu, "Английский голос", englishVoiceSpinner)
        setting(menu, "Русский голос", russianVoiceSpinner)
        setting(menu, "Скорость произношения", speedSpinner)
        voiceInfo = text("Загрузка списка установленных голосов...", 13f, muted)
        add(menu, voiceInfo, top = 7)
        add(menu, button("▶ Прослушать английский голос", white, navy) {
            say("Hello! Welcome to English Words.", Locale.US)
        }, 50, 8)
        add(menu, button("▶ Прослушать русский голос", white, navy) {
            say("Привет! Проверяем произношение.", Locale("ru", "RU"))
        }, 50, 6)
        if (ttsReady) updateOfflineVoiceControls()
        add(menu, button("Начать новый блок") { startSession() }, 54, 12)
        add(root, menu, top = 10)

        counter = text("Выбери блок в меню", 16f, navy, true)
        add(root, counter, top = 24)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = 0; progressTintList = android.content.res.ColorStateList.valueOf(blue)
        }
        add(root, progress, 6, 12)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(12), dp(30), dp(12), dp(30))
            background = rounded(white, 12)
            minimumHeight = dp(290)
        }
        add(card, text("ПЕРЕВОД", 12f, muted, true))
        prompt = text("Нажми «Меню»\nи начни урок", 37f, navy, true).apply {
            setPadding(dp(2), dp(26), dp(2), dp(14))
        }
        add(card, prompt)
        pronunciation = text("", 29f, blue, true).apply { setPadding(dp(2), dp(4), dp(2), dp(10)) }
        add(card, pronunciation)
        add(root, card, top = 18)

        status = text("Настройки открываются по кнопке «Меню»", 17f, muted)
        status.setPadding(dp(6), dp(18), dp(6), dp(16))
        add(root, status)

        listen = button("Говори перевод", navy) { listenNow() }
        add(root, listen, 64, 8)
        repeat = button("Слушать слово", white, blue) {
            current?.let { say(question(it), questionLocale()) }
        }
        add(root, repeat, 56, 12)
        val rating = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        correct = button("Знаю", Color.rgb(42, 105, 88)) { grade(true, "Отмечено: знаю") }
        incorrect = button("Не знаю", Color.rgb(165, 76, 82)) { grade(false, "Отмечено: не знаю") }
        rating.addView(correct, LinearLayout.LayoutParams(0, dp(58), 1f).apply { rightMargin = dp(6) })
        rating.addView(incorrect, LinearLayout.LayoutParams(0, dp(58), 1f).apply { leftMargin = dp(6) })
        add(root, rating, top = 12)
        skip = button("Пропустить", white, muted) { grade(false, "Пропущено") }
        add(root, skip, 50, 12)
        stats = text("", 14f, muted)
        add(root, stats, top = 20)
        updateButtons()
        if (resources.configuration.screenWidthDp >= 600) {
            val lessonViews = listOf(card, status, listen, repeat, rating, skip)
            val sidebarViews = listOf(counter, progress, stats)
            lessonViews.forEach { root.removeView(it) }
            sidebarViews.forEach { root.removeView(it) }
            val columns = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
            }
            val mainPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val sidePanel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(20), dp(16), dp(20))
                background = rounded(white, 12)
            }
            lessonViews.forEach { add(mainPanel, it, top = 10) }
            sidebarViews.forEach { add(sidePanel, it, top = 14) }
            // On an unfolded Fold the progress panel stays on the left and the
            // active word receives the larger, right-hand reading area.
            columns.addView(sidePanel, LinearLayout.LayoutParams(0, -2, 1.15f).apply {
                rightMargin = dp(16)
            })
            columns.addView(mainPanel, LinearLayout.LayoutParams(0, -2, 3f))
            add(root, columns, top = 16)
        }
    }

    private fun updateDeviceCapabilities() {
        if (!::deviceInfo.isInitialized) return
        val recognition = if (onDeviceRecognitionSupported) {
            "Автономное распознавание Android: доступно (языковые пакеты проверяются при запуске)"
        } else {
            "Автономное распознавание Android: недоступно. Используй кнопки «Знаю / Не знаю»"
        }
        deviceInfo.text = "Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}\n" +
            recognition + "\nИнтерфейс и словарь доступны без распознавания речи."
    }

    private fun toggleMenu() {
        menu.visibility = if (menu.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        menuButton.text = if (menu.visibility == View.VISIBLE) "✕  Закрыть" else "☰  Меню"
    }
    private fun updateButtons() {
        listen.visibility = if (method == 0 && onDeviceRecognitionSupported) View.VISIBLE else View.GONE
        correct.visibility = View.VISIBLE
        incorrect.visibility = View.VISIBLE
        listen.isEnabled = running && !answering
        correct.isEnabled = running && !answering
        incorrect.isEnabled = running && !answering
        repeat.isEnabled = running && !answering
        skip.isEnabled = running && !answering
    }
    private fun startSession() {
        handler.removeCallbacksAndMessages(null)
        tts.stop(); recognizer?.cancel()
        direction = dirSpinner.selectedItemPosition
        method = methodSpinner.selectedItemPosition
        if (method == 0 && !onDeviceRecognitionSupported) {
            method = 1
            methodSpinner.setSelection(1)
            status.text = "Автономное распознавание недоступно: включён режим ответа кнопками"
        }
        sessionSize = listOf(10, 20, 30, 50)[blockSpinner.selectedItemPosition]
        selectionMode = selectionSpinner.selectedItemPosition
        val candidates = words.filter { w -> when (selectionMode) {
            1 -> (0..1).any { prefs.getInt("${it}_${w.id}", -1) == 0 }
            2 -> (0..1).all { prefs.getInt("${it}_${w.id}", -1) == -1 }
            else -> true
        } }
        reviewIds = candidates.shuffled().take(sessionSize).map { it.id }
        if (reviewIds.isEmpty()) {
            running = false; status.text = "Для этого режима пока нет слов"; updateButtons(); return
        }
        index = 0; running = true; answering = false
        menu.visibility = View.GONE; menuButton.text = "☰  Меню"
        next()
    }
    private fun key(w: Word) = "${questionDirection}_${w.id}"
    private fun question(w: Word) = if (questionDirection == 0) w.en else w.ru
    private fun answer(w: Word) = if (questionDirection == 0) w.ru else w.en
    private fun questionLocale() = if (questionDirection == 0) Locale.US else Locale("ru", "RU")
    private fun answerLocale() = if (questionDirection == 0) Locale("ru", "RU") else Locale.US
    private fun next() {
        if (!running) return
        if (index >= reviewIds.size) {
            running = false; current = null; answering = false
            prompt.text = "Блок завершён!"
            pronunciation.text = ""
            counter.text = "Все ${reviewIds.size} слов проверены"
            status.text = "Открой меню, чтобы начать следующий блок"
            progress.progress = 100
            updateButtons(); refresh(); return
        }
        val w = words.first { it.id == reviewIds[index] }
        current = w
        questionDirection = if (direction == 2) (0..1).random() else direction
        answering = false
        counter.text = "Слово ${index + 1} из ${reviewIds.size}"
        progress.progress = (index * 100 / reviewIds.size)
        prompt.text = question(w)
        prompt.textSize = when { question(w).length > 20 -> 30f; question(w).length > 13 -> 38f; else -> 52f }
        // The English spelling/pronunciation must not reveal the answer in RU → EN mode.
        pronunciation.text = if (questionDirection == 0) w.pron else "Произношение появится после ответа"
        status.text = if (ttsReady) "Слушай слово..." else "Озвучивание недоступно; можно отвечать кнопками"
        updateButtons(); refresh()
        say(question(w), questionLocale()) {
            if (running && current?.id == w.id && !answering) {
                if (method == 0) handler.postDelayed({ listenNow() }, 350)
                else status.text = "Вспомни перевод и выбери ответ"
            }
        }
    }
    private fun offlineVoicesFor(locale: Locale): List<Voice> = runCatching {
        tts.voices.orEmpty()
            .filter { it.locale.language == locale.language && !it.isNetworkConnectionRequired }
            .sortedWith(
                compareByDescending<Voice> { it.locale.country == locale.country }
                    .thenByDescending { it.quality }
                    .thenBy { it.name }
            )
    }.getOrDefault(emptyList())

    private fun attachVoiceChoices(spinner: Spinner, voices: List<Voice>, preferenceKey: String) {
        val choices = if (voices.isEmpty()) {
            listOf("Офлайн-голоса не установлены")
        } else {
            listOf("Лучший доступный офлайн-голос") +
                voices.map { "${it.locale.toLanguageTag()} · ${it.name}" }
        }
        spinner.onItemSelectedListener = null
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, choices)
        spinner.isEnabled = voices.isNotEmpty()
        val savedName = prefs.getString(preferenceKey, null)
        val index = voices.indexOfFirst { it.name == savedName }
        spinner.setSelection(if (index >= 0) index + 1 else 0)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val editor = prefs.edit()
                if (position == 0 || voices.isEmpty()) editor.remove(preferenceKey)
                else editor.putString(preferenceKey, voices[position - 1].name)
                editor.apply()
            }
        }
    }

    private fun updateOfflineVoiceControls() {
        if (!::englishVoiceSpinner.isInitialized || !::voiceInfo.isInitialized) return
        attachVoiceChoices(englishVoiceSpinner, englishOfflineVoices, "voice_en")
        attachVoiceChoices(russianVoiceSpinner, russianOfflineVoices, "voice_ru")
        voiceInfo.text = if (englishOfflineVoices.isEmpty() || russianOfflineVoices.isEmpty()) {
            "Для отсутствующего языка установи офлайн-голос через настройки " +
                "синтеза речи Android. Приложение не переключается на сетевые голоса."
        } else {
            "Выбираются только установленные голоса, не требующие интернета. " +
                "Пол и естественность голоса зависят от установленного речевого движка."
        }
    }

    private fun say(value: String, locale: Locale, done: (() -> Unit)? = null) {
        if (!ttsReady) {
            status.text = "Голосовой движок ещё не готов. Ответь кнопками или повтори позже"
            done?.invoke()
            return
        }
        val available = if (locale.language == "ru") russianOfflineVoices else englishOfflineVoices
        val preferenceKey = if (locale.language == "ru") "voice_ru" else "voice_en"
        val savedName = prefs.getString(preferenceKey, null)
        val voice = available.firstOrNull { it.name == savedName } ?: available.firstOrNull()
        if (voice == null) {
            status.text = "Нет установленного офлайн-голоса для языка ${locale.language}. " +
                "Добавь голос в настройках синтеза речи Android."
            done?.invoke()
            return
        }

        tts.stop()
        if (tts.setVoice(voice) != TextToSpeech.SUCCESS) {
            status.text = "Не удалось включить офлайн-голос ${voice.name}"
            done?.invoke()
            return
        }
        val speedIndex = if (::speedSpinner.isInitialized) speedSpinner.selectedItemPosition else 2
        tts.setSpeechRate(voiceRates[speedIndex.coerceIn(0, voiceRates.lastIndex)])
        val utterance = "utt_${System.nanoTime()}"
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) {}
            override fun onDone(id: String) {
                if (id == utterance && done != null) handler.post { done() }
            }
            override fun onError(id: String) {
                if (id == utterance) handler.post {
                    status.text = "Ошибка воспроизведения офлайн-голоса"
                    done?.invoke()
                }
            }
        })
        if (tts.speak(value, TextToSpeech.QUEUE_FLUSH, null, utterance) == TextToSpeech.ERROR) {
            status.text = "Голос недоступен. Проверь офлайн-пакет языка."
            done?.invoke()
        }
    }
    private fun listenNow() {
        if (!running || method != 0 || answering) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 12)
            status.text = "Разреши доступ к микрофону и нажми кнопку ещё раз"
            return
        }
        // Never fall back to a cloud recognizer: this training mode is offline-only.
        if (!onDeviceRecognitionSupported) {
            status.text = "Автономное распознавание недоступно. " +
                "Установи офлайн-пакеты английского и русского в настройках распознавания речи Android."
            return
        }
        recognizer?.destroy()
        recognizer = try {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
        } catch (e: Exception) {
            status.text = "Не удалось запустить автономное распознавание: ${e.message}"
            return
        }
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { status.text = "🎤 Слушаю твой ответ..." }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { status.text = "Проверяю ответ..." }
            override fun onError(error: Int) {
                if (!answering) {
                    status.text = when (error) {
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
                            "Этот язык не поддерживается автономным распознаванием Android"
                        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                            "Офлайн-пакет языка не установлен. Загрузи его в настройках Android"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            "Нет разрешения на использование микрофона"
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            "Не расслышала ответ. Нажми микрофон и повтори"
                        else -> "Ошибка автономного распознавания (код $error). Попробуй ещё раз"
                    }
                }
            }
            override fun onResults(results: Bundle?) {
                if (answering) return
                val guesses = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                val w = current ?: return
                grade(guesses.any { isCorrect(it, w) }, "Распознано: ${guesses.firstOrNull() ?: "—"}")
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (questionDirection == 0) "ru-RU" else "en-US")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        try { recognizer?.startListening(intent) }
        catch (e: Exception) { status.text = "Ошибка микрофона: ${e.message}" }
    }
    private fun normalize(s: String): String = Normalizer.normalize(
        s.lowercase(Locale.ROOT).replace('ё', 'е'), Normalizer.Form.NFD
    ).replace(Regex("\\p{M}+"), "").replace(Regex("[^\\p{L}\\p{N} ]"), " ")
        .trim().replace(Regex("\\s+"), " ")
    private fun isCorrect(heard: String, w: Word): Boolean {
        val h = normalize(heard)
        val answers = answer(w).split(',', ';', '/').map { normalize(it) }.filter { it.isNotBlank() }
        return answers.any { a -> h == a || h == "to $a" || (questionDirection == 0 && h == a.removePrefix("быть ")) }
    }
    private fun grade(ok: Boolean, detail: String) {
        val w = current ?: return
        if (!running || answering) return
        answering = true
        recognizer?.cancel()
        prefs.edit().putInt(key(w), if (ok) 1 else 0).apply()
        val forms = if (formsCheck.isChecked) irregularForms[w.en.lowercase(Locale.ROOT)]?.let { "\nФормы: $it" } ?: "" else ""
        pronunciation.text = "${w.en}  •  ${w.pron}"
        status.text = (if (ok) "Окей!" else "Не окей.") + "\n$detail\nПравильно: ${answer(w)}$forms"
        updateButtons(); refresh(); index++
        say((if (ok) "Окей. " else "Не окей. ") + answer(w), answerLocale()) {
            handler.postDelayed({ next() }, 900)
        }
    }
    private fun refresh() {
        val known = words.count { w -> (0..1).any { d -> prefs.getInt("${d}_${w.id}", -1) == 1 } }
        val wrong = words.count { w -> (0..1).any { d -> prefs.getInt("${d}_${w.id}", -1) == 0 } }
        stats.text = "В словаре: ${words.size}  •  Знаю: $known  •  Ошибки: $wrong"
    }
    override fun onInit(statusCode: Int) {
        ttsReady = statusCode == TextToSpeech.SUCCESS
        if (ttsReady) {
            englishOfflineVoices = offlineVoicesFor(Locale.US)
            russianOfflineVoices = offlineVoicesFor(Locale("ru", "RU"))
        }
        runOnUiThread { updateOfflineVoiceControls() }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (running && current != null) {
            outState.putIntArray("lesson_ids", reviewIds.toIntArray())
            outState.putInt("lesson_index", index)
            outState.putInt("lesson_direction", direction)
            outState.putInt("lesson_method", method)
            outState.putInt("lesson_selection", selectionMode)
            outState.putInt("lesson_question_direction", questionDirection)
        }
    }
    private fun restoreSession(state: Bundle?) {
        val ids = state?.getIntArray("lesson_ids")?.toList() ?: return
        if (ids.isEmpty() || ids.any { id -> words.none { it.id == id } }) return
        reviewIds = ids
        index = state.getInt("lesson_index", 0)
        if (index !in reviewIds.indices) return
        direction = state.getInt("lesson_direction", 0)
        method = state.getInt("lesson_method", 0)
        if (method == 0 && !onDeviceRecognitionSupported) method = 1
        selectionMode = state.getInt("lesson_selection", 0)
        dirSpinner.setSelection(direction.coerceIn(0, 2))
        methodSpinner.setSelection(method.coerceIn(0, 1))
        selectionSpinner.setSelection(selectionMode.coerceIn(0, 2))
        blockSpinner.setSelection(listOf(10, 20, 30, 50).indexOf(ids.size).coerceAtLeast(0))
        running = true
        answering = false
        next()
    }
    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy(); tts.stop(); tts.shutdown()
        super.onDestroy()
    }
}
