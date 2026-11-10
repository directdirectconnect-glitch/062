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
import android.content.res.Configuration
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
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import java.util.UUID

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    data class Word(val id: Int, val en: String, val ru: String, val pron: String)
    data class PhraseExample(val en: String, val ru: String)
    data class Scene(
        val id: String, val titleRu: String, val titleEn: String,
        val wordIds: List<Int>, val examples: Map<Int, List<PhraseExample>>
    )
    private val scenes = mutableListOf<Scene>()
    private var activeSceneId = ""
    data class FamilyMember(val word: String, val ru: String, val pos: String, val build: String)
    data class WordFamily(
        val base: String, val baseRu: String, val basePos: String,
        val members: List<FamilyMember>, val forms: List<FamilyMember>
    )
    private val examplesByWord = mutableMapOf<String, List<PhraseExample>>()
    private val familyIndex = mutableMapOf<String, WordFamily>()
    private lateinit var familyButton: Button
    private var familyDialog: android.app.AlertDialog? = null
    private var familyExpanded = false
    private lateinit var examplesToggle: Button
    private lateinit var examplesBody: LinearLayout
    private lateinit var nextWordButton: Button
    private lateinit var transitionSpinner: Spinner
    private lateinit var lessonModeSpinner: Spinner
    private lateinit var sceneSpinner: Spinner
    private lateinit var learningPauseSpinner: Spinner
    private lateinit var reminderSpinner: Spinner
    private lateinit var learningPauseButton: Button
    private lateinit var translationText: TextView
    private lateinit var learningExamplePreview: TextView
    private lateinit var cardHeading: TextView
    private var learningMode = false
    private var learningPaused = false
    private var includeExamplesDuringLearning = false
    private var learningSequenceId = 0
    private var learningPauseSeconds = 3
    private val learningPauseOptions = listOf(3, 4, 5, 6, 10, 12)
    private val reminderHourOptions = listOf(0, 1, 2, 3, 4, 6, 8, 12, 24)
    private val reminderPermissionRequestCode = 183
    private val learningAdvanceRunnable = Runnable {
        if (running && learningMode && !learningPaused && !examplesExpanded &&
            !familyExpanded && menu.visibility != View.VISIBLE) {
            index++
            next()
        }
    }
    private var examplesExpanded = false
    private var lastGradeOk = false
    private var lastGradeDetail = ""
    private var nextDelaySeconds = 4
    private val nextDelays = listOf(0, 3, 4, 5, 7)
    private val advanceRunnable = Runnable { if (running && answering) advanceToNext() }
    private val words = mutableListOf<Word>()
    private val handler = Handler(Looper.getMainLooper())
    // Do not let playback or automatic grading continue behind Ask Word/other apps.
    private var resumeLearningAfterForeground = false
    private var resumeAnswerTimerAfterForeground = false
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
    private val vosk by lazy { OfflineVoskEngine(this) }
    private fun packagedOfflineVoice(): Boolean =
        OfflineVoskEngine.packaged(this, "ru") &&
            OfflineVoskEngine.packaged(this, "en")
    private fun anyOfflineVoice(): Boolean = packagedOfflineVoice() || onDeviceRecognitionSupported
    private var direction = 0
    private var method = 0
    private var questionDirection = 0
    private var sessionSize = 50
    private var selectionMode = 0
    private var index = 0
    private var running = false
    private var answering = false
    private val prefs by lazy { getSharedPreferences("progress_v1", MODE_PRIVATE) }
    private val sessionPrefs by lazy { getSharedPreferences("active_session_v1", MODE_PRIVATE) }
    private val historyStore by lazy { LearningHistoryStore(applicationContext) }
    private var sessionId: String = UUID.randomUUID().toString()
    private var assistedThisWord = false
    // A targeted review asks in the direction that actually needs practice.
    private var forcedReviewDirections: Map<Int, Int> = emptyMap()
    // Preserve the original block while repeatedly revisiting its unresolved words.
    private var originalBlockIds: List<Int> = emptyList()
    private var pendingBlockErrors: Map<Int, Int> = emptyMap()
    private var remediationRound = 0
    private var reviewIds: List<Int> = emptyList()
    private var current: Word? = null

    private var uiEnglish = false
    // The default follows Android's current light/dark setting.
    // 0 = light, 1 = dark, 2 = system.
    private var themeChoice = 2
    private var isDarkTheme = false
    private lateinit var themeSpinner: Spinner
    private var fontChoice = 1
    private var weightChoice = 1
    private var writingChoice = 0
    private var appearanceRoot: View? = null
    private lateinit var languageSpinner: Spinner
    private lateinit var fontSpinner: Spinner
    private lateinit var weightSpinner: Spinner
    private lateinit var writingSpinner: Spinner

    private val englishLabels = mapOf(
        "Меню" to "Menu", "НАСТРОЙКИ" to "SETTINGS",
        "Направление перевода" to "Translation direction",
        "Способ ответа" to "Answer method",
        "Размер блока" to "Block size",
        "Какие слова повторять" to "Words to practice",
        "English → Русский" to "English → Russian",
        "Русский → English" to "Russian → English",
        "Смешанный" to "Mixed",
        "Голосовая проверка" to "Voice recognition",
        "Кнопки: знаю / не знаю" to "Buttons: know / don't know",
        "10 слов" to "10 words", "20 слов" to "20 words",
        "30 слов" to "30 words", "50 слов" to "50 words",
        "Из всего словаря" to "All words",
        "Только ошибки" to "Mistakes only",
        "Ещё не проверенные" to "Not yet tested",
        "Показывать формы глагола после ответа" to "Show verb forms after answering",
        "ОЗВУЧИВАНИЕ БЕЗ ИНТЕРНЕТА" to "OFFLINE SPEECH",
        "Английский голос" to "English voice",
        "Русский голос" to "Russian voice",
        "Скорость произношения" to "Speech speed",
        "Загрузка голосов..." to "Loading voices...",
        "Загрузка списка установленных голосов..." to "Loading installed voices...",
        "▶ Прослушать английский голос" to "▶ Preview English voice",
        "▶ Прослушать русский голос" to "▶ Preview Russian voice",
        "Начать новый блок" to "Start new block",
        "Выбери блок в меню" to "Choose a block from the menu",
        "ПЕРЕВОД" to "TRANSLATION",
        "Нажми «Меню»\\nи начни урок" to "Open Menu\\nand start a lesson",
        "Настройки открываются по кнопке «Меню»" to "Open settings from Menu",
        "Говори перевод" to "Speak the translation",
        "Слушать слово" to "Listen to word",
        "Знаю" to "Know", "Не знаю" to "Don't know",
        "Пропустить" to "Skip",
        "Язык интерфейса" to "Interface language",
        "Шрифт" to "Font family",
        "Толщина шрифта" to "Font weight",
        "Стиль написания" to "Letter style",
        "ВНЕШНИЙ ВИД" to "APPEARANCE",
        "Русский" to "Russian", "Английский" to "English",
        "Обычный" to "Regular", "Полужирный" to "Semibold",
        "Жирный" to "Bold", "Прямой" to "Upright",
        "Курсив" to "Italic", "Системный" to "System",
        "Без засечек" to "Sans serif", "С засечками" to "Serif",
        "Моноширинный" to "Monospace"
    )
    private fun tr(value: String) = if (uiEnglish) englishLabels[value] ?: value else value
    private fun applyTypography(view: View) {
        if (view is TextView) {
            val family = when (fontChoice) {
                2 -> "serif"
                3 -> "monospace"
                1 -> "sans-serif"
                else -> "sans-serif"
            }
            val originallyBold = view.tag == "typography_bold" ||
                (view.tag == null && view.typeface?.isBold == true)
            if (view.tag == null) view.tag = if (originallyBold) "typography_bold" else "typography_normal"
            val weight = when (weightChoice) {
                0 -> if (originallyBold) 500 else 400
                2 -> 700
                else -> if (originallyBold) 700 else 500
            }
            val italic = writingChoice == 1
            view.typeface = if (Build.VERSION.SDK_INT >= 28) {
                Typeface.create(Typeface.create(family, Typeface.NORMAL), weight, italic)
            } else {
                Typeface.create(family, (if (weight >= 700) Typeface.BOLD else Typeface.NORMAL) or
                    (if (italic) Typeface.ITALIC else Typeface.NORMAL))
            }
        }
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) applyTypography(view.getChildAt(i))
        }
    }
    private fun saveAppearance() {
        fontChoice = fontSpinner.selectedItemPosition
        weightChoice = weightSpinner.selectedItemPosition
        writingChoice = writingSpinner.selectedItemPosition
        prefs.edit().putInt("font_family", fontChoice)
            .putInt("font_weight", weightChoice)
            .putInt("writing_style", writingChoice).apply()
        appearanceRoot?.let(::applyTypography)
        if (::menu.isInitialized) applyTypography(menu)
    }
    private fun enableLiveAppearance(spinner: Spinner) {
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (appearanceRoot != null) saveAppearance()
            }
        }
    }
    // Monochrome palettes only: no purple, green, red, or colorful accents.
    private val navy: Int get() = if (isDarkTheme) Color.rgb(238, 240, 243) else Color.rgb(26, 31, 37)
    private val muted: Int get() = if (isDarkTheme) Color.rgb(177, 182, 187) else Color.rgb(102, 110, 119)
    private val blue: Int get() = if (isDarkTheme) Color.rgb(225, 228, 232) else Color.rgb(34, 43, 54)
    private val bg: Int get() = if (isDarkTheme) Color.rgb(14, 16, 18) else Color.rgb(247, 248, 249)
    private val white: Int get() = if (isDarkTheme) Color.rgb(31, 34, 38) else Color.WHITE
    private val neutralSurface: Int get() = if (isDarkTheme) Color.rgb(48, 52, 57) else Color.rgb(233, 236, 239)
    private val neutralInk: Int get() = if (isDarkTheme) Color.rgb(235, 238, 242) else Color.rgb(38, 45, 52)
    private lateinit var menu: LinearLayout
    private lateinit var menuButton: Button
    private var settingsDialog: android.app.AlertDialog? = null
    private var examplesDialog: android.app.AlertDialog? = null
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
    private lateinit var ratingRow: LinearLayout
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
        themeChoice = prefs.getInt("ui_theme", 2).coerceIn(0, 2)
        isDarkTheme = when (themeChoice) {
            0 -> false
            1 -> true
            else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        }
        // Choose a matching native theme for dialogs, menus and spinner controls too.
        setTheme(if (isDarkTheme) android.R.style.Theme_Material_NoActionBar
            else android.R.style.Theme_Material_Light_NoActionBar)
        val json = JSONArray(assets.open("words.json").bufferedReader().use { it.readText() })
        for (i in 0 until json.length()) {
            val w = json.getJSONObject(i)
            words.add(Word(w.getInt("id"), w.getString("en"), w.getString("ru"), w.optString("pron")))
        }
        // A curated offline phrase bank, independent of Android language services.
        val phraseBank = JSONObject(assets.open("examples.json").bufferedReader().use { it.readText() })
            .getJSONObject("examples")
        val phraseKeys = phraseBank.keys()
        while (phraseKeys.hasNext()) {
            val word = phraseKeys.next()
            val phrases = phraseBank.getJSONArray(word)
            examplesByWord[word.lowercase(Locale.ROOT)] = (0 until phrases.length()).map { i ->
                val phrase = phrases.getJSONObject(i)
                PhraseExample(phrase.getString("en"), phrase.getString("ru"))
            }
        }
        val familyBank = JSONObject(assets.open("word_families.json").bufferedReader().use {
            it.readText()
        }).getJSONArray("families")
        fun parseFamilyMembers(entries: JSONArray): List<FamilyMember> =
            (0 until entries.length()).map { i ->
                val item = entries.getJSONObject(i)
                FamilyMember(item.getString("word"), item.getString("ru"),
                    item.getString("pos"), item.getString("build"))
            }
        for (i in 0 until familyBank.length()) {
            val item = familyBank.getJSONObject(i)
            val family = WordFamily(item.getString("base"), item.getString("baseRu"),
                item.getString("basePos"), parseFamilyMembers(item.getJSONArray("members")),
                parseFamilyMembers(item.getJSONArray("forms")))
            familyIndex[family.base.lowercase(Locale.ROOT)] = family
            (family.members + family.forms).forEach { member ->
                familyIndex[member.word.lowercase(Locale.ROOT)] = family
            }
        }
        val sceneBank = JSONObject(assets.open("scenes.json").bufferedReader().use {
            it.readText()
        }).getJSONArray("scenes")
        for (i in 0 until sceneBank.length()) {
            val item = sceneBank.getJSONObject(i)
            val entries = item.getJSONArray("words")
            val ids = mutableListOf<Int>()
            val contextualExamples = mutableMapOf<Int, List<PhraseExample>>()
            for (j in 0 until entries.length()) {
                val entry = entries.getJSONObject(j)
                val wordId = entry.getInt("wordId")
                if (words.none { it.id == wordId }) continue
                ids.add(wordId)
                val arr = entry.getJSONArray("examples")
                contextualExamples[wordId] = (0 until arr.length()).map { k ->
                    val example = arr.getJSONObject(k)
                    PhraseExample(example.getString("en"), example.getString("ru"))
                }.take(2)
            }
            scenes.add(Scene(item.getString("id"), item.getString("titleRu"),
                item.getString("titleEn"), ids, contextualExamples))
        }
        learningPauseSeconds = prefs.getInt("learning_pause_seconds", 3)
            .takeIf { it in learningPauseOptions } ?: 3
        nextDelaySeconds = prefs.getInt("next_delay_seconds", 4)
        onDeviceRecognitionSupported =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        tts = TextToSpeech(this, this)
        uiEnglish = prefs.getString("ui_language", "ru") == "en"
        fontChoice = prefs.getInt("font_family", 1)
        weightChoice = prefs.getInt("font_weight", 1)
        writingChoice = prefs.getInt("writing_style", 0)
        buildUi()
        // Prefer Android's saved state on rotation; use durable snapshot after process death.
        restoreSession(savedInstanceState ?: readPersistedSession())
        refresh()
        handleReminderEntry(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            setIntent(intent)
            handleReminderEntry(intent)
        }
    }

    // Android displayed a notification: only react to a notification tap.
    private fun handleReminderEntry(incoming: Intent?) {
        if (incoming?.getBooleanExtra(ReminderScheduler.EXTRA_START_REVIEW, false) != true) return
        val requested = incoming.getStringExtra(ReminderScheduler.EXTRA_REVIEW_MODE) ?: "choose"
        incoming.removeExtra(ReminderScheduler.EXTRA_START_REVIEW)
        incoming.removeExtra(ReminderScheduler.EXTRA_REVIEW_MODE)
        when (requested) {
            "learn" -> showLearningOptions(fromReminder = true)
            "check" -> startSession(modeOverride = "check", fromReminder = true)
            else -> android.app.AlertDialog.Builder(this)
                .setTitle(if (uiEnglish) "Practice English" else "Давай проверим слова!")
                .setItems(if (uiEnglish)
                    arrayOf("Learn words — listen", "Check words — test yourself")
                    else arrayOf("Изучать — слушать слова", "Проверить — вспомнить слова")
                ) { _, which ->
                    if (which == 0) showLearningOptions(fromReminder = true)
                    else startSession(modeOverride = "check", fromReminder = true)
                }
                .setNegativeButton(if (uiEnglish) "Not now" else "Не сейчас", null)
                .show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == reminderPermissionRequestCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                ReminderScheduler.schedule(this)
            } else {
                Toast.makeText(this, if (uiEnglish)
                    "Enable notifications in Android settings to receive reminders."
                    else "Для напоминаний разреши уведомления в настройках Android.",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()
    private fun rounded(color: Int, radius: Int = 12): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun text(value: String, size: Float, color: Int = navy, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = tr(value); textSize = size; setTextColor(color)
            gravity = Gravity.CENTER
            if (bold) setTypeface(null, Typeface.BOLD)
        }
    private fun button(value: String, color: Int = blue, ink: Int = white, action: () -> Unit): Button =
        Button(this).apply {
            text = tr(value); textSize = 15f; isAllCaps = false
            setTextColor(ink); background = rounded(color, 10)
            setOnClickListener { action() }
        }
    private fun add(parent: LinearLayout, view: View, height: Int = -2, top: Int = 0) {
        parent.addView(view, LinearLayout.LayoutParams(-1, if (height == -2) -2 else dp(height)).apply {
            topMargin = dp(top)
        })
    }
    private fun spinner(options: List<String>): Spinner = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, options.map(::tr))
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
        appearanceRoot = root

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(navy, 12)
            setPadding(dp(14), dp(10), dp(12), dp(10))
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        add(brand, text("ENGLISH WORDS", 21f, white, true).apply { gravity = Gravity.START })
        add(brand, text("ОФЛАЙН-СЛОВАРЬ · ${words.size} СЛОВ", 11f, white).apply { gravity = Gravity.START; alpha = .78f }, top = 3)
        header.addView(brand, LinearLayout.LayoutParams(0, dp(60), 1f))
        menuButton = button("⋮", neutralSurface, neutralInk) { showOverflowMenu() }.apply {
            textSize = 26f
            contentDescription = if (uiEnglish) "More options" else "Дополнительные действия"
        }
        header.addView(menuButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        add(root, header)
        // Both listening and recall are primary actions on phones and Fold screens.
        val modeActions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        modeRow.addView(button(if (uiEnglish) "Learn words" else "Изучать",
            blue, white) {
            showLearningOptions()
        }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { rightMargin = dp(5) })
        modeRow.addView(button(if (uiEnglish) "Check words" else "Проверять",
            neutralSurface, neutralInk) {
            startSession(modeOverride = "check")
        }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { leftMargin = dp(5) })
        add(modeActions, modeRow)
        add(modeActions, button(if (uiEnglish) "Ask a word" else "Спроси слово",
            neutralSurface, neutralInk) {
            showAskWordOptions()
        }, 50, 8)
        add(root, modeActions, top = 12)

        menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(18))
            background = rounded(white, 12)
            visibility = View.GONE
        }
        add(menu, text("НАСТРОЙКИ", 16f, navy, true))
        add(menu, text("ВНЕШНИЙ ВИД", 16f, navy, true), top = 10)
        languageSpinner = spinner(listOf("Русский", "Английский")).apply {
            setSelection(if (uiEnglish) 1 else 0)
        }
        themeSpinner = spinner(
            if (uiEnglish) listOf("Light (white)", "Dark (black)", "As on Android")
            else listOf("Светлая (белая)", "Тёмная (чёрная)", "Как в Android")
        ).apply {
            setSelection(themeChoice)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position != themeChoice) {
                        themeChoice = position
                        prefs.edit().putInt("ui_theme", position).apply()
                        // Activity state restores the current word and answer on recreation.
                        recreate()
                    }
                }
            }
        }
        fontSpinner = spinner(listOf("Системный", "Без засечек", "С засечками", "Моноширинный")).apply {
            setSelection(fontChoice.coerceIn(0, 3))
        }
        weightSpinner = spinner(listOf("Обычный", "Полужирный", "Жирный")).apply {
            setSelection(weightChoice.coerceIn(0, 2))
        }
        writingSpinner = spinner(listOf("Прямой", "Курсив")).apply {
            setSelection(writingChoice.coerceIn(0, 1))
        }
        setting(menu, "Язык интерфейса", languageSpinner)
        setting(menu, if (uiEnglish) "Display theme" else "Тема оформления", themeSpinner)
        setting(menu, "Шрифт", fontSpinner)
        setting(menu, "Толщина шрифта", weightSpinner)
        setting(menu, "Стиль написания", writingSpinner)
        enableLiveAppearance(fontSpinner)
        enableLiveAppearance(weightSpinner)
        enableLiveAppearance(writingSpinner)
        add(menu, text(if (uiEnglish) "Font changes apply instantly, including during a lesson." else
            "Шрифт меняется сразу, даже во время урока.", 12f, muted), top = 8)
        add(menu, button(if (uiEnglish) "Apply interface language" else "Применить язык интерфейса", white, blue) {
            val newEnglish = languageSpinner.selectedItemPosition == 1
            if (newEnglish != uiEnglish) {
                prefs.edit().putString("ui_language", if (newEnglish) "en" else "ru").apply()
                recreate()
            }
        }, 52, 10)

        deviceInfo = text("", 13f, muted).apply { gravity = Gravity.START }
        add(menu, deviceInfo, top = 10)
        updateDeviceCapabilities()
        dirSpinner = spinner(listOf("English → Русский", "Русский → English", "Смешанный"))
        methodSpinner = spinner(listOf("Голосовая проверка", "Кнопки: знаю / не знаю"))
        blockSpinner = spinner(listOf("10 слов", "20 слов", "30 слов", "50 слов")).apply { setSelection(3) }
        selectionSpinner = spinner(listOf("Из всего словаря", "Только ошибки", "Ещё не проверенные", "С примерами из жизни", "С семьями слов"))
        lessonModeSpinner = spinner(if (uiEnglish)
            listOf("Learn — listen and read", "Check — recall words")
            else listOf("Изучение — слушать и читать", "Проверка — вспомнить слово")
        ).apply { setSelection(1) }
        sceneSpinner = spinner(
            listOf(if (uiEnglish) "All topics" else "Все темы") +
                scenes.map { if (uiEnglish) it.titleEn else it.titleRu }
        ).apply {
            val oldId = prefs.getString("selected_scene", "") ?: ""
            setSelection((scenes.indexOfFirst { it.id == oldId } + 1).coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val choice = scenes.getOrNull(position - 1)?.id ?: ""
                    prefs.edit().putString("selected_scene", choice).apply()
                }
            }
        }
        learningPauseSpinner = spinner(learningPauseOptions.map {
            if (uiEnglish) "$it seconds" else "$it секунд"
        }).apply {
            setSelection(learningPauseOptions.indexOf(learningPauseSeconds).coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    learningPauseSeconds = learningPauseOptions[position.coerceIn(learningPauseOptions.indices)]
                    prefs.edit().putInt("learning_pause_seconds", learningPauseSeconds).apply()
                }
            }
        }
        setting(menu, if (uiEnglish) "Learning mode" else "Режим занятия", lessonModeSpinner)
        setting(menu, if (uiEnglish) "Location / scene" else "Где я нахожусь", sceneSpinner)
        setting(menu, if (uiEnglish) "Pause after translation" else
            "Пауза после перевода (изучение)", learningPauseSpinner)
        setting(menu, "Направление перевода", dirSpinner)
        setting(menu, "Способ ответа", methodSpinner)
        setting(menu, "Размер блока", blockSpinner)
        transitionSpinner = spinner(
            if (uiEnglish) listOf("Manual — Next word", "After 3 seconds", "After 4 seconds",
                "After 5 seconds", "After 7 seconds")
            else listOf("Вручную — Следующее слово", "Через 3 секунды", "Через 4 секунды",
                "Через 5 секунд", "Через 7 секунд")
        ).apply {
            setSelection(nextDelays.indexOf(nextDelaySeconds).coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    nextDelaySeconds = nextDelays[position.coerceIn(nextDelays.indices)]
                    prefs.edit().putInt("next_delay_seconds", nextDelaySeconds).apply()
                    if (::nextWordButton.isInitialized && running && answering) scheduleAdvance()
                }
            }
        }
        setting(menu, if (uiEnglish) "Next word" else "Переход к следующему слову", transitionSpinner)
        setting(menu, "Какие слова повторять", selectionSpinner)
        add(menu, text(if (uiEnglish) "PRACTICE REMINDERS" else "НАПОМИНАНИЯ О ЗАНЯТИЯХ",
            16f, navy, true), top = 16)
        reminderSpinner = spinner(reminderHourOptions.map {
            if (it == 0) {
                if (uiEnglish) "Off" else "Выключены"
            } else if (uiEnglish) {
                "Every $it hours"
            } else {
                "Каждые $it ч"
            }
        }).apply {
            setSelection(reminderHourOptions.indexOf(ReminderScheduler.hours(this@MainActivity))
                .coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val hours = reminderHourOptions[position.coerceIn(reminderHourOptions.indices)]
                    if (ReminderScheduler.hours(this@MainActivity) != hours) {
                        prefs.edit().putInt(ReminderScheduler.PREF_INTERVAL_HOURS, hours).apply()
                        ReminderScheduler.schedule(this@MainActivity, hours)
                        if (hours > 0 && Build.VERSION.SDK_INT >= 33 &&
                            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                                PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                                reminderPermissionRequestCode)
                        }
                    }
                }
            }
        }
        setting(menu, if (uiEnglish) "Reminder frequency" else "Периодичность", reminderSpinner)
        add(menu, text(if (uiEnglish)
            "Local notifications. Android may delay them to save battery."
            else "Локальные уведомления. Android может немного задерживать их ради экономии батареи.",
            12f, muted), top = 6)
        val historyCheck = CheckBox(this).apply {
            text = if (uiEnglish) "Save practice history on this device" else
                "Сохранять историю ответов на устройстве"
            isChecked = prefs.getBoolean("learning_history_enabled", true)
            setOnCheckedChangeListener { _, enabled ->
                prefs.edit().putBoolean("learning_history_enabled", enabled).apply()
            }
        }
        add(menu, historyCheck, top = 12)
        add(menu, button(if (uiEnglish) "Learning history" else "История ответов",
            white, blue) { showLearningHistory() }, 50, 8)
        add(menu, button(if (uiEnglish) "Review due words" else "Что повторить",
            neutralSurface, neutralInk) { showReviewRecommendations() }, 50, 8)
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
        add(menu, button("Начать новый блок") {
            if (lessonModeSpinner.selectedItemPosition == 0) showLearningOptions()
            else startSession(modeOverride = "check")
        }, 54, 12)
        // Settings live in a separate dialog reached from the standard three-dot menu.
        // No controls from this panel occupy the lesson screen.
        val settingsScroll = ScrollView(this).apply {
            setPadding(dp(8), dp(8), dp(8), dp(8))
            addView(menu)
        }
        settingsDialog = android.app.AlertDialog.Builder(this)
            .setTitle(if (uiEnglish) "Settings" else "Настройки")
            .setView(settingsScroll)
            .setPositiveButton(if (uiEnglish) "Close" else "Закрыть", null)
            .create().apply {
                setOnDismissListener {
                    menu.visibility = View.GONE
                    if (running && learningMode && !learningPaused) resumeLearningWord()
                    else if (running && answering) scheduleAdvance()
                }
            }

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
        cardHeading = text("ПЕРЕВОД", 12f, muted, true)
        add(card, cardHeading)
        prompt = text(if (uiEnglish) "Open ⋮ and start a lesson" else
            "Открой ⋮ и начни урок", 37f, navy, true).apply {
            setPadding(dp(2), dp(26), dp(2), dp(14))
        }
        add(card, prompt)
        translationText = text("", 28f, navy, true).apply {
            gravity = Gravity.CENTER; visibility = View.GONE
            setPadding(dp(8), dp(3), dp(8), dp(6))
        }
        add(card, translationText)
        pronunciation = text("", 29f, blue, true).apply { setPadding(dp(2), dp(4), dp(2), dp(10)) }
        add(card, pronunciation)
        learningExamplePreview = text("", 15f, muted).apply {
            visibility = View.GONE
            setPadding(dp(9), dp(9), dp(9), dp(2))
        }
        add(card, learningExamplePreview)
        add(root, card, top = 18)
        examplesToggle = button(if (uiEnglish) "Everyday phrases  ▾" else "Примеры из жизни  ▾", white, blue) {
            toggleExamples()
        }.apply { visibility = View.GONE }
        add(root, examplesToggle, 52, 12)
        familyButton = button(if (uiEnglish) "Word family  ▾" else "Семья слова  ▾",
            white, navy) {
            openFamily()
        }.apply { visibility = View.GONE }
        add(root, familyButton, 52, 8)
        examplesBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(white, 12)
            visibility = View.GONE
        }
        // Phrase examples are displayed in a scrollable dialog instead of
        // pushing the lesson controls off screen on compact Fold displays.
        status = text(if (uiEnglish) "Open settings from the three-dot menu" else
            "Настройки находятся в меню ⋮", 15f, muted)
        status.setPadding(dp(6), dp(18), dp(6), dp(16))
        add(root, status)

        listen = button("Говори перевод", navy) { listenNow() }
        add(root, listen, 64, 8)
        repeat = button("Слушать слово", white, blue) {
            current?.let { word ->
                if (learningMode) {
                    learningPaused = false
                    narrateLearningWord()
                    updateButtons()
                } else {
                    handler.removeCallbacks(advanceRunnable)
                    say(question(word), questionLocale()) {
                        if (running && current?.id == word.id && answering) scheduleAdvance()
                    }
                }
            }
        }
        add(root, repeat, 56, 12)
        learningPauseButton = button(if (uiEnglish) "Pause listening" else "Пауза",
            neutralSurface, neutralInk) {
            if (running && learningMode) {
                learningPaused = !learningPaused
                if (learningPaused) {
                    pauseLearningAudio()
                    status.text = if (uiEnglish) "Listening paused" else "Прослушивание на паузе"
                } else {
                    narrateLearningWord()
                }
                updateButtons()
            }
        }.apply { visibility = View.GONE }
        add(root, learningPauseButton, 52, 8)
        val rating = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        ratingRow = rating
        correct = button("Знаю", blue) { grade(true, "Отмечено: знаю") }
        incorrect = button("Не знаю", neutralSurface, neutralInk) { grade(false, "Отмечено: не знаю") }
        rating.addView(correct, LinearLayout.LayoutParams(0, dp(58), 1f).apply { rightMargin = dp(6) })
        rating.addView(incorrect, LinearLayout.LayoutParams(0, dp(58), 1f).apply { leftMargin = dp(6) })
        add(root, rating, top = 12)
        skip = button("Пропустить", white, muted) {
            grade(false, "Пропущено", skipped = true)
        }
        add(root, skip, 50, 12)
        nextWordButton = button(if (uiEnglish) "Next word →" else "Следующее слово →") {
            if (running && learningMode) {
                pauseLearningAudio()
                index++
                next()
            } else if (running && answering) advanceToNext()
        }.apply { visibility = View.GONE }
        add(root, nextWordButton, 54, 12)
        stats = text("", 14f, muted)
        add(root, stats, top = 20)
        updateButtons()
        if (resources.configuration.screenWidthDp >= 600) {
            val lessonViews = listOf(card, examplesToggle, familyButton, status, listen, repeat,
                learningPauseButton, rating, skip, nextWordButton)
            val sidebarViews = listOf(modeActions, counter, progress, stats)
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
        applyTypography(root)
    }

    private fun updateDeviceCapabilities() {
        if (!::deviceInfo.isInitialized) return
        val recognition = if (packagedOfflineVoice()) {
            "Встроенный Vosk RU/EN: доступен без интернета. Первое включение готовит локальные модели."
        } else if (onDeviceRecognitionSupported) {
            "Автономное распознавание Android: доступно (пакеты проверяются при запуске)"
        } else {
            "Автономное распознавание недоступно. Используй кнопки «Знаю / Не знаю»"
        }
        deviceInfo.text = "Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}\n" +
            recognition + "\nИнтерфейс и словарь доступны без распознавания речи."
    }

    private fun showOverflowMenu() {
        val popup = PopupMenu(this, menuButton)
        popup.menu.add(0, 1, 0, if (uiEnglish) "Appearance and settings" else "Оформление и настройки")
        popup.menu.add(0, 2, 1, if (uiEnglish) "Start new block" else "Начать новый блок")
        popup.menu.add(0, 3, 2, if (uiEnglish) "About the app" else "О приложении")
        popup.menu.add(0, 4, 3, if (uiEnglish) "Learning history" else "История ответов")
        popup.menu.add(0, 5, 4, if (uiEnglish) "Review due words" else "Что повторить")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> { openSettingsDialog(); true }
                2 -> { openSettingsDialog(); true }
                3 -> {
                    android.app.AlertDialog.Builder(this)
                        .setTitle("English Words")
                        .setMessage(if (uiEnglish)
                            "Offline vocabulary practice • version 0.9.7 preview"
                            else "Автономная речь RU/EN • версия 0.9.7 (тестовая)")
                        .setPositiveButton("OK", null).show()
                    true
                }
                4 -> { showLearningHistory(); true }
                5 -> { showReviewRecommendations(); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun openSettingsDialog() {
        handler.removeCallbacks(advanceRunnable)
        if (learningMode) pauseLearningAudio()
        menu.visibility = View.VISIBLE
        settingsDialog?.show()
        settingsDialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92f).toInt().coerceAtMost(dp(560)),
            -2
        )
        applyTypography(menu)
    }

    // The same pre-start checkbox is used for the two learning modes.
    private fun showLearningOptions(fromReminder: Boolean = false) {
        ExampleModeSetup.show(this,
            if (uiEnglish) "Learn words" else "Изучение слов") { checked ->
            startSession(modeOverride = "learn", fromReminder = fromReminder,
                includeExamplesOverride = checked)
        }
    }

    private fun showAskWordOptions() {
        ExampleModeSetup.show(this,
            if (uiEnglish) "Ask a word" else "Спроси слово") { checked ->
            startActivity(Intent(this, AskWordActivity::class.java).apply {
                putExtra(AskWordActivity.EXTRA_INCLUDE_EXAMPLES, checked)
            })
        }
    }

    private fun updateButtons() {
        val testing = !learningMode
        listen.visibility = if (testing && method == 0 && anyOfflineVoice())
            View.VISIBLE else View.GONE
        ratingRow.visibility = if (testing) View.VISIBLE else View.GONE
        correct.isEnabled = running && testing && !answering
        incorrect.isEnabled = running && testing && !answering
        listen.isEnabled = running && testing && !answering
        repeat.isEnabled = running && (learningMode || !answering)
        repeat.text = if (learningMode) {
            if (uiEnglish) "Repeat English + translation" else "Повторить слово и перевод"
        } else if (uiEnglish) "Listen to word" else "Слушать слово"
        learningPauseButton.visibility = if (running && learningMode)
            View.VISIBLE else View.GONE
        learningPauseButton.text = if (learningPaused) {
            if (uiEnglish) "Resume listening" else "Продолжить"
        } else {
            if (uiEnglish) "Pause listening" else "Пауза"
        }
        skip.visibility = if (testing) View.VISIBLE else View.GONE
        skip.isEnabled = running && testing && !answering
        nextWordButton.visibility = if (running && (learningMode || answering))
            View.VISIBLE else View.GONE
    }

    private fun startSession(
        modeOverride: String? = null,
        fromReminder: Boolean = false,
        abandonConfirmed: Boolean = false,
        includeExamplesOverride: Boolean? = null
    ) {
        // Never silently discard the unfinished correction loop.
        if (running && pendingBlockErrors.isNotEmpty() && !abandonConfirmed) {
            android.app.AlertDialog.Builder(this)
                .setTitle(if (uiEnglish) "Unresolved words" else "Есть невыученные слова")
                .setMessage(if (uiEnglish)
                    "${pendingBlockErrors.size} words still require an independent answer. " +
                        "Starting another lesson will abandon this correction loop."
                    else "Осталось исправить ${pendingBlockErrors.size} слов. " +
                        "Новый урок прервёт текущий блок, не засчитывая эти слова как изученные.")
                .setPositiveButton(if (uiEnglish) "Abandon block" else "Прервать блок") { _, _ ->
                    startSession(modeOverride, fromReminder, abandonConfirmed = true,
                        includeExamplesOverride = includeExamplesOverride)
                }
                .setNegativeButton(if (uiEnglish) "Continue review" else "Продолжить повторение", null)
                .show()
            return
        }
        handler.removeCallbacksAndMessages(null)
        pauseLearningAudio()
        examplesDialog?.dismiss()
        familyDialog?.dismiss()
        examplesExpanded = false
        familyExpanded = false
        tts.stop(); recognizer?.cancel(); vosk.stop()
        learningMode = when (modeOverride) {
            "learn" -> true
            "check" -> false
            else -> lessonModeSpinner.selectedItemPosition == 0
        }
        lessonModeSpinner.setSelection(if (learningMode) 0 else 1)
        includeExamplesDuringLearning = includeExamplesOverride ?: ExampleModeSetup.selected(this)
        learningPaused = false
        assistedThisWord = false
        sessionId = UUID.randomUUID().toString()
        forcedReviewDirections = emptyMap()
        pendingBlockErrors = emptyMap()
        originalBlockIds = emptyList()
        remediationRound = 0
        direction = dirSpinner.selectedItemPosition
        method = methodSpinner.selectedItemPosition
        if (!learningMode && method == 0 && !anyOfflineVoice()) {
            method = 1
            methodSpinner.setSelection(1)
            status.text = "Автономное распознавание недоступно: включён режим ответа кнопками"
        }
        sessionSize = if (fromReminder) 10
            else listOf(10, 20, 30, 50)[blockSpinner.selectedItemPosition]
        selectionMode = selectionSpinner.selectedItemPosition
        activeSceneId = scenes.getOrNull(sceneSpinner.selectedItemPosition - 1)?.id ?: ""
        val selectedScene = scenes.firstOrNull { it.id == activeSceneId }
        val candidateWords = if (selectedScene != null) {
            words.filter { it.id in selectedScene.wordIds }
        } else words.filter { w ->
            when (selectionMode) {
                1 -> (0..1).any { prefs.getInt("${it}_${w.id}", -1) == 0 }
                2 -> (0..1).all { prefs.getInt("${it}_${w.id}", -1) == -1 }
                3 -> examplesByWord.containsKey(w.en.lowercase(Locale.ROOT))
                4 -> familyIndex.containsKey(w.en.lowercase(Locale.ROOT))
                else -> true
            }
        }
        val ordered = if (fromReminder) candidateWords.shuffled().sortedBy { word ->
            val attempts = (0..1).map { prefs.getInt("${it}_${word.id}", -1) }
            if (learningMode) {
                if (attempts.all { it == -1 }) 0 else if (attempts.any { it == 0 }) 1 else 2
            } else {
                if (attempts.any { it == 0 }) 0 else if (attempts.any { it == -1 }) 1 else 2
            }
        } else candidateWords.shuffled()
        reviewIds = ordered.take(sessionSize).map { it.id }
        originalBlockIds = reviewIds
        if (reviewIds.isEmpty()) {
            running = false
            current = null
            persistActiveSession()
            status.text = if (uiEnglish) "No words in this selection" else
                "Для выбранной подборки пока нет слов"
            updateButtons()
            return
        }
        index = 0; running = true; answering = false
        settingsDialog?.dismiss()
        menu.visibility = View.GONE
        next()
    }
    private fun key(w: Word) = "${questionDirection}_${w.id}"
    private fun question(w: Word) = if (questionDirection == 0) w.en else w.ru
    private fun answer(w: Word) = if (questionDirection == 0) w.ru else w.en
    private fun questionLocale() = if (questionDirection == 0) Locale.US else Locale("ru", "RU")
    private fun answerLocale() = if (questionDirection == 0) Locale("ru", "RU") else Locale.US
    private fun next(restoringAnswer: Boolean = false, savedDirection: Int? = null) {
        if (!running) return
        handler.removeCallbacks(advanceRunnable)
        pauseLearningAudio()
        learningExamplePreview.visibility = View.GONE
        learningExamplePreview.text = ""
        examplesExpanded = false
        familyExpanded = false
        examplesBody.visibility = View.GONE
        examplesToggle.visibility = View.GONE
        familyButton.visibility = View.GONE
        nextWordButton.visibility = View.GONE
        if (index >= reviewIds.size) {
            if (!learningMode && pendingBlockErrors.isNotEmpty()) {
                val retryIds = BlockRemediationPlanner.nextRound(
                    originalBlockIds.ifEmpty { reviewIds }, pendingBlockErrors
                )
                if (retryIds.isNotEmpty()) {
                    reviewIds = retryIds
                    forcedReviewDirections = pendingBlockErrors.toMap()
                    remediationRound++
                    index = 0
                    next()
                    return
                }
            }
            running = false; current = null; answering = false
            pendingBlockErrors = emptyMap()
            prompt.text = if (learningMode) {
                if (uiEnglish) "Listening finished!" else "Прослушивание завершено!"
            } else if (remediationRound > 0) {
                if (uiEnglish) "All mistakes corrected!" else "Все ошибки исправлены!"
            } else if (uiEnglish) "Block completed!" else "Блок завершён!"
            translationText.visibility = View.GONE
            pronunciation.text = ""
            counter.text = "Все ${reviewIds.size} слов проверены"
            status.text = if (uiEnglish) "Open ⋮ to begin another block" else
                "Открой ⋮, чтобы начать следующий блок"
            progress.progress = 100
            updateButtons(); refresh()
            persistActiveSession()
            return
        }
        val w = words.first { it.id == reviewIds[index] }
        current = w
        assistedThisWord = false
        questionDirection = if (learningMode) 0 else
            (savedDirection ?: forcedReviewDirections[w.id] ?:
                if (direction == 2) (0..1).random() else direction)
        answering = false
        counter.text = if (!learningMode && remediationRound > 0) {
            if (uiEnglish) "Correction round $remediationRound · ${index + 1}/${reviewIds.size}"
            else "Повторение ошибок · круг $remediationRound · ${index + 1} из ${reviewIds.size}"
        } else "Слово ${index + 1} из ${reviewIds.size}"
        progress.progress = (index * 100 / reviewIds.size)
        cardHeading.text = if (learningMode) {
            if (uiEnglish) "LEARN — LISTEN" else "ИЗУЧЕНИЕ — СЛУШАТЬ"
        } else if (uiEnglish) "TRANSLATE" else "ПЕРЕВОД"
        translationText.visibility = if (learningMode) View.VISIBLE else View.GONE
        if (learningMode) {
            prompt.text = w.en
            prompt.textSize = when {
                w.en.length > 20 -> 34f
                w.en.length > 13 -> 42f
                else -> 52f
            }
            translationText.text = w.ru
            pronunciation.text = w.pron
            pronunciation.textSize = 19f
            status.text = if (uiEnglish) "Listen to English, then Russian." else
                "Слушай английское слово и русский перевод."
            updateExamplesControls(w)
            updateFamilyControl(w)
            updateButtons(); refresh()
            persistActiveSession()
            if (!learningPaused) narrateLearningWord()
            return
        }
        pronunciation.textSize = 29f
        prompt.text = question(w)
        prompt.textSize = when { question(w).length > 20 -> 30f; question(w).length > 13 -> 38f; else -> 52f }
        // The English spelling/pronunciation must not reveal the answer in RU → EN mode.
        pronunciation.text = if (questionDirection == 0) w.pron else "Произношение появится после ответа"
        status.text = if (ttsReady) "Слушай слово..." else "Озвучивание недоступно; можно отвечать кнопками"
        updateExamplesControls(w)
        updateFamilyControl(w)
        if (restoringAnswer) {
            answering = true
            updateFamilyControl(w)
            pronunciation.text = "${w.en}  •  ${w.pron}"
            status.text = (if (lastGradeOk) "Окей!" else "Не окей.") +
                "\n$lastGradeDetail\nПравильно: ${answer(w)}"
            nextWordButton.visibility = View.VISIBLE
            updateButtons(); refresh()
            persistActiveSession()
            scheduleAdvance()
            return
        }
        updateButtons(); refresh()
        persistActiveSession()
        say(question(w), questionLocale()) {
            if (running && current?.id == w.id && !answering) {
                if (method == 0) handler.postDelayed({ listenNow() }, 350)
                else status.text = "Вспомни перевод и выбери ответ"
            }
        }
    }
    private fun updateFamilyControl(word: Word) {
        // In RU → EN mode, reveal the English family only after the answer.
        val visible = familyIndex.containsKey(word.en.lowercase(Locale.ROOT)) &&
            (questionDirection == 0 || answering)
        familyButton.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) {
            val size = familyIndex[word.en.lowercase(Locale.ROOT)]?.members?.size ?: 0
            familyButton.text = if (uiEnglish) "Word family ($size)  ›"
                else "Семья слова ($size)  ›"
        }
    }

    private fun openFamily() {
        val target = current ?: return
        val family = familyIndex[target.en.lowercase(Locale.ROOT)] ?: return
        if (familyDialog?.isShowing == true) return
        familyExpanded = true
        if (running && !answering && !learningMode) assistedThisWord = true
        handler.removeCallbacks(advanceRunnable)
        if (learningMode) pauseLearningAudio()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(16))
            setBackgroundColor(white)
        }
        // Main word and translation remain the strongest visual elements.
        add(root, text(family.base, 30f, navy, true).apply { gravity = Gravity.START })
        add(root, text(family.baseRu, 22f, navy).apply { gravity = Gravity.START }, top = 3)
        add(root, text(family.basePos, 11f, muted).apply { gravity = Gravity.START }, top = 3)
        add(root, text(if (uiEnglish) "RELATED WORDS" else "ПРОИЗВОДНЫЕ СЛОВА",
            12f, muted, true).apply { gravity = Gravity.START }, top = 20)
        family.members.forEach { member -> addFamilyRow(root, member) }
        if (family.forms.isNotEmpty()) {
            add(root, text(if (uiEnglish) "FORMS" else "ФОРМЫ СЛОВА",
                12f, muted, true).apply { gravity = Gravity.START }, top = 20)
            family.forms.forEach { member -> addFamilyRow(root, member) }
        }
        add(root, text(
            if (uiEnglish) "Related words are learned and checked separately."
            else "Родственные слова изучаются и проверяются отдельно.",
            12f, muted).apply { gravity = Gravity.START }, top = 15)
        applyTypography(root)
        val scroller = ScrollView(this).apply {
            setFillViewport(false)
            addView(root)
        }
        familyDialog = android.app.AlertDialog.Builder(this)
            .setTitle(if (uiEnglish) "Word family" else "Семья слова")
            .setView(scroller)
            .setPositiveButton(if (uiEnglish) "Close" else "Закрыть", null)
            .create().apply {
                setOnDismissListener {
                    familyExpanded = false
                    familyDialog = null
                    if (running && learningMode && !learningPaused) resumeLearningWord()
                    else if (running && answering) scheduleAdvance()
                }
            }
        familyDialog?.show()
        familyDialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.94f).toInt().coerceAtMost(dp(660)),
            (resources.displayMetrics.heightPixels * 0.78f).toInt()
        )
    }

    private fun addFamilyRow(parent: LinearLayout, item: FamilyMember) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(bg, 10)
            setPadding(dp(12), dp(10), dp(8), dp(10))
        }
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        add(labels, text(item.word, 20f, navy, true).apply {
            gravity = Gravity.START
        })
        add(labels, text(item.ru, 16f, navy).apply {
            gravity = Gravity.START
        }, top = 2)
        // Grammatical class is secondary and intentionally abbreviated.
        add(labels, text(item.pos + "  ·  " + item.build, 11f, muted).apply {
            gravity = Gravity.START
        }, top = 4)
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(button("▶", neutralSurface, neutralInk) {
            handler.removeCallbacks(advanceRunnable)
            say(item.word, Locale.US) {
                if (running && answering && !familyExpanded) scheduleAdvance()
            }
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { leftMargin = dp(8) })
        add(parent, row, top = 8)
    }

    private fun scenePhrases(word: Word): List<PhraseExample> {
        val contextual = scenes.firstOrNull { it.id == activeSceneId }?.examples?.get(word.id)
        return contextual ?: examplesByWord[word.en.lowercase(Locale.ROOT)].orEmpty()
    }
    private fun updateExamplesControls(word: Word) {
        val phrases = scenePhrases(word)
        examplesToggle.visibility = if (phrases.isEmpty()) View.GONE else View.VISIBLE
        examplesToggle.text = if (uiEnglish) "Everyday phrases (${phrases.size})  ▾"
            else "Примеры из жизни (${phrases.size})  ▾"
        examplesBody.visibility = View.GONE
    }

    private fun toggleExamples() {
        val word = current ?: return
        val phrases = scenePhrases(word)
        if (phrases.isEmpty()) return
        if (examplesDialog?.isShowing == true) return
        examplesExpanded = true
        if (running && !answering && !learningMode) assistedThisWord = true
        handler.removeCallbacks(advanceRunnable)
        if (learningMode) pauseLearningAudio()
        showExamples(word, phrases)
        (examplesBody.parent as? android.view.ViewGroup)?.removeView(examplesBody)
        val phraseScroll = ScrollView(this).apply {
            setPadding(dp(8), dp(6), dp(8), dp(8))
            addView(examplesBody)
        }
        examplesDialog = android.app.AlertDialog.Builder(this)
            .setTitle(if (uiEnglish) "Everyday phrases" else "Примеры из жизни")
            .setView(phraseScroll)
            .setPositiveButton(if (uiEnglish) "Close" else "Закрыть", null)
            .create().apply {
                setOnDismissListener {
                    examplesExpanded = false
                    examplesBody.visibility = View.GONE
                    examplesDialog = null
                    if (running && learningMode && !learningPaused) resumeLearningWord()
                    else if (running && answering) scheduleAdvance()
                }
            }
        examplesDialog?.show()
        val dialogWidth = (resources.displayMetrics.widthPixels * 0.94f).toInt()
            .coerceAtMost(dp(660))
        val dialogHeight = (resources.displayMetrics.heightPixels * 0.77f).toInt()
        examplesDialog?.window?.setLayout(dialogWidth, dialogHeight)
    }

    private fun showExamples(word: Word, phrases: List<PhraseExample>) {
        examplesBody.removeAllViews()
        examplesBody.visibility = View.VISIBLE
        add(examplesBody,
            text(if (uiEnglish) "COMMON PHRASES WITH: ${word.en.uppercase(Locale.ROOT)}"
            else "ПОЛЕЗНЫЕ ФРАЗЫ: ${word.en.uppercase(Locale.ROOT)}", 13f, muted, true).apply {
                gravity = Gravity.START
            }, top = 6)
        phrases.forEachIndexed { i, phrase ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(bg, 10)
                setPadding(dp(10), dp(10), dp(8), dp(10))
            }
            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            add(labels, text("${i + 1}. ${phrase.en}", 16f, navy, true).apply {
                gravity = Gravity.START
            })
            add(labels, text(phrase.ru, 14f, muted).apply { gravity = Gravity.START }, top = 4)
            row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(button("▶", white, blue) {
                handler.removeCallbacks(advanceRunnable)
                say(phrase.en, Locale.US) {
                    if (running && answering && !examplesExpanded) scheduleAdvance()
                }
            }, LinearLayout.LayoutParams(dp(52), dp(48)).apply {
                leftMargin = dp(8)
            })
            add(examplesBody, row, top = 8)
        }
        applyTypography(examplesBody)
    }

    private fun pauseLearningAudio() {
        learningSequenceId++
        handler.removeCallbacks(learningAdvanceRunnable)
        if (::tts.isInitialized) tts.stop()
    }

    private fun resumeLearningWord() {
        if (running && learningMode && !learningPaused && !examplesExpanded &&
            !familyExpanded && menu.visibility != View.VISIBLE) narrateLearningWord()
    }

    private fun narrateLearningWord() {
        val word = current ?: return
        if (!running || !learningMode || learningPaused ||
            examplesExpanded || familyExpanded || menu.visibility == View.VISIBLE) return
        if (!ttsReady || englishOfflineVoices.isEmpty() || russianOfflineVoices.isEmpty()) {
            status.text = if (uiEnglish) "Install both offline TTS voices in Android settings."
                else "Для прослушивания нужны офлайн-голоса английского и русского языка Android."
            return
        }
        pauseLearningAudio()
        val playbackId = learningSequenceId
        val wordId = word.id
        fun stillCurrent() = running && learningMode && !learningPaused &&
            current?.id == wordId && playbackId == learningSequenceId &&
            !examplesExpanded && !familyExpanded && menu.visibility != View.VISIBLE
        val selectedExamples = if (includeExamplesDuringLearning) scenePhrases(word) else emptyList()
        if (includeExamplesDuringLearning && selectedExamples.isEmpty()) {
            status.text = if (uiEnglish) "No curated examples for this word yet."
                else "Для этого слова примеры пока не добавлены."
        }
        fun scheduleNext() {
            if (!stillCurrent()) return
            status.text = if (uiEnglish)
                "Next word in $learningPauseSeconds seconds"
            else "Следующее слово через $learningPauseSeconds с"
            handler.postDelayed(learningAdvanceRunnable, learningPauseSeconds * 1000L)
        }
        fun speakExampleAt(index: Int) {
            if (!stillCurrent()) return
            if (index >= selectedExamples.size) {
                scheduleNext()
                return
            }
            val example = selectedExamples[index]
            learningExamplePreview.text = "${example.en}\n${example.ru}"
            learningExamplePreview.visibility = View.VISIBLE
            status.text = if (uiEnglish) "Usage example" else "Пример употребления"
            say(example.en, Locale.US) {
                if (stillCurrent()) {
                    say(example.ru, Locale("ru", "RU")) {
                        if (stillCurrent()) speakExampleAt(index + 1)
                    }
                }
            }
        }
        status.text = if (uiEnglish) "English → Russian" else
            "Английское слово → русский перевод"
        say(word.en, Locale.US) {
            if (stillCurrent()) {
                say(word.ru, Locale("ru", "RU")) {
                    if (stillCurrent()) speakExampleAt(0)
                }
            }
        }
    }

    private fun scheduleAdvance() {
        handler.removeCallbacks(advanceRunnable)
        if (!running || !answering || examplesExpanded || familyExpanded ||
            menu.visibility == View.VISIBLE || nextDelaySeconds == 0) return
        handler.postDelayed(advanceRunnable, nextDelaySeconds * 1000L)
    }

    private fun advanceToNext() {
        if (!running || !answering) return
        handler.removeCallbacks(advanceRunnable)
        tts.stop()
        index++
        next()
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
        // Prefer bundled RU/EN Vosk for genuine offline recognition on Android 8+.
        val word = current ?: return
        val spokenLanguage = if (questionDirection == 0) "ru" else "en"
        if (OfflineVoskEngine.packaged(this, spokenLanguage)) {
            vosk.listen(spokenLanguage,
                onStatus = { message ->
                    if (running && !answering && current?.id == word.id) status.text = message
                },
                onTranscript = { phrase ->
                    if (running && !answering && current?.id == word.id) {
                        if (isCorrect(phrase, word)) {
                            grade(true, "Vosk: $phrase", methodSource = "asr")
                        } else {
                            status.text = if (uiEnglish)
                                "Vosk heard '$phrase'. Repeat or use Know / Don't know."
                            else "Vosk услышал: «$phrase». Повтори или ответь кнопками."
                        }
                    }
                },
                onError = { reason ->
                    if (running && !answering) {
                        method = 1
                        methodSpinner.setSelection(1)
                        updateButtons()
                        status.text = if (uiEnglish)
                            "Offline Vosk unavailable: $reason. Use buttons."
                        else "Vosk недоступен: $reason. Отвечай кнопками."
                    }
                })
            return
        }
        // On-device Android ASR is allowed only if locally supported; never cloud.
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
                    if (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                        error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) {
                        // The service may exist without an installed on-device language
                        // model. Keep the exercise usable offline via manual answers.
                        method = 1
                        methodSpinner.setSelection(1)
                        updateButtons()
                        status.text = if (uiEnglish)
                            "Offline speech language unavailable. Use Know / Don't know."
                        else "Нет локального пакета распознавания. Отвечай кнопками «Знаю / Не знаю»."
                        return
                    }
                    status.text = when (error) {
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
                if (guesses.isEmpty() || guesses.all { it.isBlank() }) {
                    status.text = if (uiEnglish) "Speech not recognized; retry or use buttons" else
                        "Речь не распознана — повтори или ответь кнопками"
                    return
                }
                if (guesses.any { isCorrect(it, w) }) {
                    grade(true, "Распознано: ${guesses.firstOrNull() ?: "—"}",
                        methodSource = "asr")
                } else {
                    // ASR can mishear a correct answer. Do not block progression based
                    // solely on a negative recognizer result without learner confirmation.
                    status.text = if (uiEnglish)
                        "Recognition did not confirm the answer. Retry or mark Know / Don't know."
                    else "Распознавание не подтвердило ответ. Повтори или выбери «Знаю / Не знаю»."
                }
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
    private fun grade(
        ok: Boolean, detail: String, methodSource: String = "manual",
        skipped: Boolean = false
    ) {
        val w = current ?: return
        if (!running || answering) return
        answering = true
        recognizer?.cancel()
        vosk.stop()
        // A hinted "know" is practice, not an independently verified recall.
        if (!ok || !assistedThisWord) {
            prefs.edit().putInt(key(w), if (ok) 1 else 0).apply()
        }
        if (!learningMode) {
            pendingBlockErrors = BlockRemediationPlanner.afterAnswer(
                pendingBlockErrors,
                BlockRemediationPlanner.Answer(w.id, questionDirection, ok, assistedThisWord)
            )
        }
        if (prefs.getBoolean("learning_history_enabled", true) && !learningMode) {
            try {
                historyStore.record(
                    sessionId, w.id, questionDirection, methodSource,
                    if (skipped) "skipped" else if (ok) "correct" else "incorrect",
                    assistedThisWord, activeSceneId
                )
            } catch (_: Exception) {
                // History is supplementary: a storage problem must not invalidate a lesson.
                status.text = if (uiEnglish) "Could not save learning history" else
                    "Не удалось сохранить историю ответов"
            }
        }
        val forms = if (formsCheck.isChecked) irregularForms[w.en.lowercase(Locale.ROOT)]?.let { "\nФормы: $it" } ?: "" else ""
        pronunciation.text = "${w.en}  •  ${w.pron}"
        status.text = (if (ok) "Окей!" else "Не окей.") +
            "\n$detail\nПравильно: ${answer(w)}$forms" +
            if (assistedThisWord) {
                if (uiEnglish) "\nAssisted answer: a clean retry is required."
                else "\nОтвет с подсказкой: повторим без неё."
            } else if (pendingBlockErrors.isNotEmpty()) {
                if (uiEnglish) "\nUnresolved in this block: ${pendingBlockErrors.size}"
                else "\nОсталось исправить в блоке: ${pendingBlockErrors.size}"
            } else ""
        lastGradeOk = ok
        lastGradeDetail = detail
        nextWordButton.visibility = View.VISIBLE
        updateFamilyControl(w)
        updateButtons(); refresh()
        persistActiveSession()
        say((if (ok) "Окей. " else "Не окей. ") + answer(w), answerLocale()) {
            if (running && current?.id == w.id && answering) scheduleAdvance()
        }
    }
    private fun refresh() {
        val known = words.count { w -> (0..1).any { d -> prefs.getInt("${d}_${w.id}", -1) == 1 } }
        val wrong = words.count { w -> (0..1).any { d -> prefs.getInt("${d}_${w.id}", -1) == 0 } }
        stats.text = "В словаре: ${words.size}  •  Знаю: $known  •  Ошибки: $wrong" +
            if (running && !learningMode && pendingBlockErrors.isNotEmpty()) {
                if (uiEnglish) "\nCurrent block · unresolved: ${pendingBlockErrors.size}"
                else "\nТекущий блок · ошибки: ${pendingBlockErrors.size}"
            } else ""
    }
    override fun onInit(statusCode: Int) {
        ttsReady = statusCode == TextToSpeech.SUCCESS
        if (ttsReady) {
            englishOfflineVoices = offlineVoicesFor(Locale.US)
            russianOfflineVoices = offlineVoicesFor(Locale("ru", "RU"))
        }
        runOnUiThread { updateOfflineVoiceControls() }
    }
    /**
     * Starting a review never silently overwrites the ongoing lesson.
     * Reviews use manual assessment because speech recognizer failures are ambiguous.
     */
    private fun startDueReview(confirmReplace: Boolean = true) {
        if (!prefs.getBoolean("learning_history_enabled", true)) {
            Toast.makeText(this,
                if (uiEnglish) "Enable learning history in Settings first."
                else "Сначала включи историю ответов в настройках.",
                Toast.LENGTH_LONG).show()
            return
        }
        if (running && confirmReplace) {
            android.app.AlertDialog.Builder(this)
                .setTitle(if (uiEnglish) "Replace current lesson?" else "Заменить текущий урок?")
                .setMessage(if (uiEnglish)
                    "The current lesson will be replaced by a due-word review."
                    else "Текущий урок будет заменён повторением слов. Его прогресс уже записан.")
                .setPositiveButton(if (uiEnglish) "Review" else "Повторить") { _, _ ->
                    startDueReview(false)
                }
                .setNegativeButton(if (uiEnglish) "Cancel" else "Отмена", null)
                .show()
            return
        }
        val due = try {
            historyStore.dueReviews().filter { candidate ->
                words.any { it.id == candidate.wordId }
            }
        } catch (_: Exception) {
            Toast.makeText(this,
                if (uiEnglish) "Review history is unavailable."
                else "Не удалось прочитать историю повторений.",
                Toast.LENGTH_LONG).show()
            return
        }
        // Each word appears once per mini-block; choose its highest-priority direction.
        val unique = linkedMapOf<Int, LearningReviewPlanner.DueWord>()
        due.forEach { unique.putIfAbsent(it.wordId, it) }
        val count = listOf(10, 20, 30, 50).getOrElse(blockSpinner.selectedItemPosition) { 50 }
        val selected = unique.values.take(count)
        if (selected.isEmpty()) {
            android.app.AlertDialog.Builder(this)
                .setTitle(if (uiEnglish) "No words due" else "Повторять пока нечего")
                .setMessage(if (uiEnglish)
                    "The stored unassisted manual checks do not have words due yet."
                    else "По самостоятельным ответам пока нет слов, срок повторения которых наступил.")
                .setPositiveButton("OK", null).show()
            return
        }
        handler.removeCallbacksAndMessages(null)
        pauseLearningAudio()
        examplesDialog?.dismiss()
        familyDialog?.dismiss()
        tts.stop()
        recognizer?.cancel()
        vosk.stop()
        examplesExpanded = false
        familyExpanded = false
        learningMode = false
        learningPaused = false
        lessonModeSpinner.setSelection(1)
        direction = 2
        dirSpinner.setSelection(2)
        method = 1
        methodSpinner.setSelection(1)
        selectionMode = 0
        selectionSpinner.setSelection(0)
        activeSceneId = ""
        sceneSpinner.setSelection(0)
        sessionId = UUID.randomUUID().toString()
        reviewIds = selected.map { it.wordId }
        originalBlockIds = reviewIds
        pendingBlockErrors = emptyMap()
        remediationRound = 0
        forcedReviewDirections = selected.associate { it.wordId to it.direction }
        index = 0
        running = true
        answering = false
        assistedThisWord = false
        lastGradeDetail = ""
        settingsDialog?.dismiss()
        menu.visibility = View.GONE
        next()
    }

    private fun showReviewRecommendations() {
        if (!prefs.getBoolean("learning_history_enabled", true)) {
            android.app.AlertDialog.Builder(this)
                .setTitle(if (uiEnglish) "Review suggestions" else "Что повторить")
                .setMessage(if (uiEnglish) "Enable learning history in Settings to use suggestions."
                    else "Включи сохранение истории в настройках, чтобы получать рекомендации.")
                .setPositiveButton("OK", null).show()
            return
        }
        val due = try {
            historyStore.dueReviews().filter { c -> words.any { it.id == c.wordId } }
        } catch (_: Exception) {
            android.app.AlertDialog.Builder(this)
                .setTitle(if (uiEnglish) "Review suggestions" else "Что повторить")
                .setMessage(if (uiEnglish) "Cannot read local learning history."
                    else "Ошибка чтения локальной истории обучения.")
                .setPositiveButton("OK", null).show()
            return
        }
        val unique = due.distinctBy { it.wordId }
        val lines = mutableListOf<String>()
        if (unique.isEmpty()) {
            lines.add(if (uiEnglish)
                "No reviews are due now. After a self-rated success the first suggested review is tomorrow."
                else "Сейчас нет слов к повторению. После отметки «Знаю» первая повторная проверка назначается на следующий день.")
        } else {
            lines.add(if (uiEnglish) "${unique.size} words due. These are scheduling hints, not verified knowledge."
                else "К повторению: ${unique.size} слов. Это ориентир, а не подтверждение знания.")
            lines.add("")
            unique.take(15).forEach { item ->
                val word = words.firstOrNull { it.id == item.wordId }?.en ?: "#${item.wordId}"
                val dir = if (item.direction == 0) "EN→RU" else "RU→EN"
                val reason = if (item.unresolvedMistake) {
                    if (uiEnglish) "unresolved error" else "ошибка или пропуск"
                } else if (uiEnglish) "interval reached" else "наступил срок"
                lines.add("• $word · $dir — $reason")
            }
            if (unique.size > 15) lines.add(if (uiEnglish) "And ${unique.size - 15} more..."
                else "И ещё ${unique.size - 15} слов...")
        }
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle(if (uiEnglish) "Review suggestions · offline" else "Что повторить · офлайн")
            .setMessage(lines.joinToString("\n"))
            .setNegativeButton(if (uiEnglish) "Close" else "Закрыть", null)
        if (unique.isNotEmpty()) {
            dialog.setPositiveButton(if (uiEnglish) "Review now" else "Повторить сейчас") { _, _ ->
                startDueReview()
            }
        }
        dialog.show()
    }

    /** Show observed results only; no causal conclusions with sparse evidence. */
    private fun showLearningHistory() {
        val totals: LearningHistoryStore.Totals
        val difficult: List<LearningHistoryStore.RepeatedDifficulty>
        try {
            totals = historyStore.totals()
            difficult = historyStore.repeatedDifficulties()
        } catch (_: Exception) {
            android.app.AlertDialog.Builder(this)
                .setTitle(if (uiEnglish) "Learning history" else "История ответов")
                .setMessage(if (uiEnglish) "The local history could not be read" else
                    "Не удалось прочитать локальную историю ответов")
                .setPositiveButton("OK", null).show()
            return
        }
        val lines = mutableListOf<String>()
        if (uiEnglish) {
            lines.add("Recorded checks: ${totals.attempts}")
            lines.add("Self-rated/recognized correct: ${totals.correct}")
            lines.add("Incorrect: ${totals.incorrect}  ·  Skipped: ${totals.skipped}")
            lines.add("Assisted (examples shown first): ${totals.assisted}")
        } else {
            lines.add("Записано проверок: ${totals.attempts}")
            lines.add("Отмечено «знаю» / распознано верно: ${totals.correct}")
            lines.add("Не знаю: ${totals.incorrect}  ·  Пропущено: ${totals.skipped}")
            lines.add("С подсказками до ответа: ${totals.assisted}")
        }
        lines.add("")
        if (difficult.isEmpty()) {
            lines.add(if (uiEnglish) "Not enough independent answers to identify repeated difficulties yet."
                else "Пока недостаточно самостоятельных ответов для выявления повторных затруднений.")
        } else {
            lines.add(if (uiEnglish) "Repeated difficulties (observations, not diagnoses):"
                else "Повторные затруднения (наблюдения, не диагноз):")
            difficult.forEach { item ->
                val word = words.firstOrNull { it.id == item.wordId }?.en ?: "#${item.wordId}"
                val dir = if (item.direction == 0) "EN→RU" else "RU→EN"
                lines.add("• $word ($dir): ${item.mistakes}/${item.attempts}")
            }
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(if (uiEnglish) "Learning history · offline" else "История ответов · офлайн")
            .setMessage(lines.joinToString("\n"))
            .setPositiveButton(if (uiEnglish) "Close" else "Закрыть", null)
            .setNeutralButton(if (uiEnglish) "Delete history" else "Удалить историю") { _, _ ->
                android.app.AlertDialog.Builder(this)
                    .setTitle(if (uiEnglish) "Delete recorded checks?" else "Удалить историю проверок?")
                    .setMessage(if (uiEnglish) "Vocabulary progress will remain unchanged."
                        else "Результаты изученных слов останутся без изменений.")
                    .setPositiveButton(if (uiEnglish) "Delete" else "Удалить") { _, _ ->
                        historyStore.clear()
                    }
                    .setNegativeButton(if (uiEnglish) "Cancel" else "Отмена", null)
                    .show()
            }
            .show()
    }

    /** Keep the active lesson independent of Activity recreation and process lifetime. */
    private fun persistActiveSession(commitNow: Boolean = false) {
        val word = current
        val editor = sessionPrefs.edit()
        if (!running || word == null || index !in reviewIds.indices ||
            reviewIds[index] != word.id) {
            editor.remove("snapshot")
        } else {
            val snapshot = JSONObject().apply {
                put("schema", 1)
                put("ids", JSONArray(reviewIds))
                put("index", index)
                put("direction", direction)
                put("method", method)
                put("selection", selectionMode)
                put("questionDirection", questionDirection)
                put("answered", answering)
                put("lastGradeOk", lastGradeOk)
                put("lastGradeDetail", lastGradeDetail)
                put("learningMode", learningMode)
                put("learningPaused", learningPaused)
                put("includeExamplesDuringLearning", includeExamplesDuringLearning)
                put("sceneId", activeSceneId)
                put("sessionId", sessionId)
                put("assisted", assistedThisWord)
                put("originalBlockIds", JSONArray(originalBlockIds))
                put("pendingBlockIds", JSONArray(pendingBlockErrors.keys.toList()))
                put("pendingBlockDirections", JSONArray(pendingBlockErrors.values.toList()))
                put("remediationRound", remediationRound)
                put("forcedDirections", JSONArray(reviewIds.map {
                    forcedReviewDirections[it] ?: -1
                }))
            }
            editor.putString("snapshot", snapshot.toString())
        }
        if (commitNow) editor.commit() else editor.apply()
    }

    private fun readPersistedSession(): Bundle? {
        val raw = sessionPrefs.getString("snapshot", null) ?: return null
        return try {
            val snapshot = JSONObject(raw)
            require(snapshot.getInt("schema") == 1)
            val arr = snapshot.getJSONArray("ids")
            require(arr.length() in 1..50)
            val ids = IntArray(arr.length()) { arr.getInt(it) }
            require(ids.toSet().size == ids.size)
            require(ids.all { id -> words.any { it.id == id } })
            val idx = snapshot.getInt("index")
            val dir = snapshot.getInt("direction")
            val mode = snapshot.getInt("method")
            val selection = snapshot.getInt("selection")
            val questionDir = snapshot.getInt("questionDirection")
            require(idx in ids.indices && dir in 0..2 && mode in 0..1)
            require(selection in 0..4 && questionDir in 0..1)
            Bundle().apply {
                putIntArray("lesson_ids", ids)
                putInt("lesson_index", idx)
                putInt("lesson_direction", dir)
                putInt("lesson_method", mode)
                putInt("lesson_selection", selection)
                putInt("lesson_question_direction", questionDir)
                putBoolean("lesson_answered", snapshot.optBoolean("answered"))
                putBoolean("lesson_last_grade_ok", snapshot.optBoolean("lastGradeOk"))
                putString("lesson_last_grade_detail", snapshot.optString("lastGradeDetail", ""))
                putBoolean("lesson_learning_mode", snapshot.optBoolean("learningMode"))
                putBoolean("lesson_learning_paused", snapshot.optBoolean("learningPaused"))
                putBoolean("lesson_include_examples",
                    snapshot.optBoolean("includeExamplesDuringLearning", false))
                putString("lesson_scene_id", snapshot.optString("sceneId", ""))
                putString("lesson_session_id", snapshot.optString("sessionId", ""))
                putBoolean("lesson_assisted", snapshot.optBoolean("assisted"))
                val forced = snapshot.optJSONArray("forcedDirections")
                val forcedArray = IntArray(ids.size) { i -> forced?.optInt(i, -1) ?: -1 }
                require(forcedArray.all { it in -1..1 })
                putIntArray("lesson_forced_directions", forcedArray)
                val originalJson = snapshot.optJSONArray("originalBlockIds")
                val original = if (originalJson == null) ids else IntArray(originalJson.length()) {
                    originalJson.getInt(it)
                }
                require(original.isNotEmpty() && original.size <= 50)
                require(original.toSet().size == original.size)
                require(original.all { id -> words.any { it.id == id } })
                require(ids.all { it in original })
                val pendingIdsJson = snapshot.optJSONArray("pendingBlockIds")
                val pendingDirectionsJson = snapshot.optJSONArray("pendingBlockDirections")
                val pendingIds = if (pendingIdsJson == null) intArrayOf() else
                    IntArray(pendingIdsJson.length()) { pendingIdsJson.getInt(it) }
                val pendingDirections = if (pendingDirectionsJson == null) intArrayOf() else
                    IntArray(pendingDirectionsJson.length()) { pendingDirectionsJson.getInt(it) }
                require(pendingIds.size == pendingDirections.size)
                require(pendingIds.toSet().size == pendingIds.size)
                require(pendingIds.all { it in original })
                require(pendingDirections.all { it in 0..1 })
                val retryRound = snapshot.optInt("remediationRound", 0)
                require(retryRound in 0..100000)
                putIntArray("lesson_original_block_ids", original)
                putIntArray("lesson_pending_block_ids", pendingIds)
                putIntArray("lesson_pending_block_directions", pendingDirections)
                putInt("lesson_remediation_round", retryRound)
            }
        } catch (_: Exception) {
            sessionPrefs.edit().remove("snapshot").apply()
            null
        }
    }

    override fun onPause() {
        resumeLearningAfterForeground = running && learningMode && !learningPaused
        resumeAnswerTimerAfterForeground = running && !learningMode && answering
        handler.removeCallbacks(advanceRunnable)
        pauseLearningAudio()
        if (::tts.isInitialized) tts.stop()
        recognizer?.cancel()
        vosk.stop()
        persistActiveSession(commitNow = true)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (resumeLearningAfterForeground) {
            resumeLearningAfterForeground = false
            handler.post { if (running && learningMode && !learningPaused) resumeLearningWord() }
        }
        if (resumeAnswerTimerAfterForeground) {
            resumeAnswerTimerAfterForeground = false
            handler.post { if (running && !learningMode && answering) scheduleAdvance() }
        }
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
            outState.putBoolean("lesson_answered", answering)
            outState.putBoolean("lesson_examples_expanded", examplesExpanded)
            outState.putBoolean("lesson_family_expanded", familyExpanded)
            outState.putBoolean("lesson_last_grade_ok", lastGradeOk)
            outState.putString("lesson_last_grade_detail", lastGradeDetail)
            outState.putBoolean("lesson_learning_mode", learningMode)
            outState.putBoolean("lesson_learning_paused", learningPaused)
            outState.putBoolean("lesson_include_examples", includeExamplesDuringLearning)
            outState.putString("lesson_scene_id", activeSceneId)
            outState.putString("lesson_session_id", sessionId)
            outState.putBoolean("lesson_assisted", assistedThisWord)
            outState.putIntArray("lesson_original_block_ids", originalBlockIds.toIntArray())
            outState.putIntArray("lesson_pending_block_ids", pendingBlockErrors.keys.toIntArray())
            outState.putIntArray("lesson_pending_block_directions", pendingBlockErrors.values.toIntArray())
            outState.putInt("lesson_remediation_round", remediationRound)
            outState.putIntArray("lesson_forced_directions",
                reviewIds.map { forcedReviewDirections[it] ?: -1 }.toIntArray())
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
        if (method == 0 && !anyOfflineVoice()) method = 1
        selectionMode = state.getInt("lesson_selection", 0)
        val savedOriginal = state.getIntArray("lesson_original_block_ids")?.toList().orEmpty()
        originalBlockIds = savedOriginal.takeIf { it.isNotEmpty() &&
            it.size <= 50 && it.distinct().size == it.size &&
            it.all { id -> words.any { word -> word.id == id } } &&
            ids.all { it in savedOriginal }
        } ?: ids
        val missedIds = (state.getIntArray("lesson_pending_block_ids") ?: intArrayOf())
        val missedDirections = (state.getIntArray("lesson_pending_block_directions") ?: intArrayOf())
        pendingBlockErrors = if (missedIds.size == missedDirections.size &&
            missedIds.toSet().size == missedIds.size &&
            missedIds.all { it in originalBlockIds } &&
            missedDirections.all { it in 0..1 }
        ) {
            missedIds.zip(missedDirections.toList()).toMap()
        } else emptyMap()
        remediationRound = state.getInt("lesson_remediation_round", 0).coerceIn(0, 100000)
        val restoredDirections = state.getIntArray("lesson_forced_directions")
        forcedReviewDirections = if (restoredDirections?.size == ids.size) {
            ids.zip(restoredDirections.toList())
                .filter { (_, dir) -> dir in 0..1 }
                .toMap()
        } else emptyMap()
        learningMode = state.getBoolean("lesson_learning_mode", false)
        learningPaused = state.getBoolean("lesson_learning_paused", false)
        includeExamplesDuringLearning = state.getBoolean("lesson_include_examples",
            ExampleModeSetup.selected(this))
        activeSceneId = state.getString("lesson_scene_id", "") ?: ""
        sessionId = state.getString("lesson_session_id", "")
            ?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        lessonModeSpinner.setSelection(if (learningMode) 0 else 1)
        sceneSpinner.setSelection((scenes.indexOfFirst { it.id == activeSceneId } + 1).coerceAtLeast(0))
        dirSpinner.setSelection(direction.coerceIn(0, 2))
        methodSpinner.setSelection(method.coerceIn(0, 1))
        selectionSpinner.setSelection(selectionMode.coerceIn(0, 4))
        blockSpinner.setSelection(
            listOf(10, 20, 30, 50).indexOfFirst { it >= originalBlockIds.size }
                .coerceAtLeast(0)
        )
        running = true
        answering = false
        lastGradeOk = state.getBoolean("lesson_last_grade_ok", false)
        lastGradeDetail = state.getString("lesson_last_grade_detail", "") ?: ""
        next(
            restoringAnswer = state.getBoolean("lesson_answered", false),
            savedDirection = state.getInt("lesson_question_direction", direction)
        )
        assistedThisWord = state.getBoolean("lesson_assisted", false)
        persistActiveSession()
        if (state.getBoolean("lesson_examples_expanded", false)) toggleExamples()
        if (state.getBoolean("lesson_family_expanded", false)) openFamily()
    }
    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        examplesDialog?.dismiss()
        familyDialog?.dismiss()
        settingsDialog?.dismiss()
        pauseLearningAudio()
        recognizer?.destroy(); vosk.stop(); tts.stop(); tts.shutdown()
        super.onDestroy()
    }
}
