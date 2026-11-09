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
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
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

    private val navy = Color.rgb(17, 55, 34)
    private val muted = Color.rgb(83, 99, 84)
    private val blue = Color.rgb(15, 91, 49)
    private val bg = Color.rgb(245, 249, 241)
    private val white = Color.WHITE
    private val red = Color.rgb(204, 18, 22)
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
        tts = TextToSpeech(this, this)
        buildUi()
        refresh()
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()
    private fun rounded(color: Int, radius: Int = 18): GradientDrawable = GradientDrawable().apply {
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
            text = value; textSize = 16f; isAllCaps = false
            setTextColor(ink); background = rounded(color, 16)
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
            setPadding(dp(18), dp(16), dp(18), dp(24))
        }
        scroll.addView(root)
        setContentView(scroll)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(navy, 14)
            setPadding(dp(14), dp(10), dp(12), dp(10))
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        add(brand, text("English Words", 22f, white, true).apply { gravity = Gravity.START })
        add(brand, text("ОФЛАЙН-СЛОВАРЬ · ${words.size} СЛОВ", 11f, Color.rgb(203, 227, 206)).apply { gravity = Gravity.START }, top = 3)
        header.addView(brand, LinearLayout.LayoutParams(0, dp(60), 1f))
        menuButton = button("☰  Меню", Color.rgb(234, 242, 232), navy) { toggleMenu() }
        header.addView(menuButton, LinearLayout.LayoutParams(dp(112), dp(48)))
        add(root, header)

        menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(18))
            background = rounded(white, 14)
            visibility = View.GONE
        }
        add(menu, text("НАСТРОЙКИ ОБУЧЕНИЯ", 18f, navy, true))
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
            background = rounded(white, 14)
            minimumHeight = dp(290)
        }
        add(card, text("ПЕРЕВЕДИ СЛОВО", 13f, muted, true))
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

        listen = button("🎤  Говори перевод", navy) { listenNow() }
        add(root, listen, 64, 8)
        repeat = button("🔊  Слушать слово", white, blue) {
            current?.let { say(question(it), questionLocale()) }
        }
        add(root, repeat, 56, 12)
        val rating = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        correct = button("✓  Знаю", Color.rgb(32, 151, 109)) { grade(true, "Отмечено: знаю") }
        incorrect = button("✕  Не знаю", Color.rgb(205, 72, 81)) { grade(false, "Отмечено: не знаю") }
        rating.addView(correct, LinearLayout.LayoutParams(0, dp(58), 1f).apply { rightMargin = dp(6) })
        rating.addView(incorrect, LinearLayout.LayoutParams(0, dp(58), 1f).apply { leftMargin = dp(6) })
        add(root, rating, top = 12)
        skip = button("Пропустить", white, muted) { grade(false, "Пропущено") }
        add(root, skip, 50, 12)
        stats = text("", 14f, muted)
        add(root, stats, top = 20)
        updateButtons()
    }

    private fun toggleMenu() {
        menu.visibility = if (menu.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        menuButton.text = if (menu.visibility == View.VISIBLE) "✕  Закрыть" else "☰  Меню"
    }
    private fun updateButtons() {
        listen.visibility = if (method == 0) View.VISIBLE else View.GONE
        correct.visibility = if (method == 1) View.VISIBLE else View.GONE
        incorrect.visibility = if (method == 1) View.VISIBLE else View.GONE
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
        status.text = "Слушай слово..."
        updateButtons(); refresh()
        say(question(w), questionLocale()) {
            if (running && current?.id == w.id && !answering) {
                if (method == 0) handler.postDelayed({ listenNow() }, 350)
                else status.text = "Вспомни перевод и выбери ответ"
            }
        }
    }
    private fun say(value: String, locale: Locale, done: (() -> Unit)? = null) {
        if (!ttsReady) { status.text = "Ожидание голосового движка"; done?.invoke(); return }
        tts.stop(); tts.language = locale
        val utterance = "utt_${System.nanoTime()}"
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) {}
            override fun onDone(id: String) { if (id == utterance && done != null) handler.post { done() } }
            override fun onError(id: String) { if (id == utterance && done != null) handler.post { done() } }
        })
        tts.speak(value, TextToSpeech.QUEUE_FLUSH, null, utterance)
    }
    private fun listenNow() {
        if (!running || method != 0 || answering) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 12)
            status.text = "Разреши доступ к микрофону и нажми кнопку ещё раз"
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "На телефоне не найдена служба распознавания речи"; return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { status.text = "🎤 Слушаю твой ответ..." }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { status.text = "Проверяю ответ..." }
            override fun onError(error: Int) { if (!answering) status.text = "Не расслышала (код $error). Нажми микрофон и повтори ответ" }
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
        if (ttsReady) tts.language = Locale.US
    }
    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy(); tts.stop(); tts.shutdown()
        super.onDestroy()
    }
}
