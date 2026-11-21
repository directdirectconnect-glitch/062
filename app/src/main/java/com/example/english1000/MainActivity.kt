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
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.View
import android.widget.*
import android.widget.ImageButton
import android.widget.ImageView
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import java.util.UUID

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    data class Word(
        val id: Int, val en: String, val ru: String, val pron: String,
        val order: Int, val primaryRu: String, val ipaUs: String, val ipaUk: String,
        val topic: String, val level: String, val learnEnabled: Boolean, val testEnabled: Boolean,
        val acceptedRu: List<String>, val acceptedEn: List<String>, val examplesEn: List<String>,
        val family: List<String>, val collocations: List<String>, val phrasalVerbs: List<String>,
        val grammar: Map<String, String>,
    )
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
    private lateinit var cardHeading: TextView
    private lateinit var wordCard: LinearLayout
    private lateinit var lessonToolbar: LinearLayout
    private lateinit var lessonNumber: TextView
    private lateinit var lessonWordPosition: TextView
    private lateinit var previousLesson: Button
    private lateinit var nextLesson: Button
    private lateinit var lessonSpeaker: ImageButton
    private var learningPlan: LearningLessonPlan? = null
    private var finishedLessonSelection: FinishedLessonSelection? = null
    private lateinit var finishedLessonPanel: LinearLayout
    private lateinit var finishedLessonBack: Button
    private lateinit var finishedLessonNumber: Button
    private lateinit var finishedLessonForward: Button
    private lateinit var exerciseOptions: LinearLayout
    private lateinit var embeddedOptionsButton: Button
    private var embeddedOptionsExpanded = false
    private val acousticPermissionRequestCode = 741
    private var automaticAudioCheckDone = false
    private var automaticAudioCheckRunning = false
    private var automaticAudioRepairAttempted = false
    private lateinit var wordImage: ImageView
    private val visualCatalog by lazy { VisualVocabularyCatalog(applicationContext) }
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
    private var lastGradeSkipped = false
    private var lastGradeDetail = ""
    private var nextDelaySeconds = 4
    private var voiceResponseSeconds = 4
    private val voiceResponseOptions = listOf(3, 4, 5, 7)
    private val nextDelays = listOf(0) + voiceResponseOptions
    private val words = mutableListOf<Word>()
    private val handler = Handler(Looper.getMainLooper())
    // Do not let playback or automatic grading continue behind Ask Word/other apps.
    private var resumeLearningAfterForeground = false
    private var resumeAnswerTimerAfterForeground = false
    private lateinit var tts: TextToSpeech
    private var ttsReady = false
    private val bundledSpeech by lazy { SherpaOfflineSpeech(this) }
    @Volatile private var bundledSpeechReady = false
    private var englishOfflineVoices: List<Voice> = emptyList()
    private var russianOfflineVoices: List<Voice> = emptyList()
    private val voiceRates = floatArrayOf(0.80f, 0.90f, 1.00f, 1.10f, 1.20f)
    private lateinit var englishVoiceSpinner: Spinner
    private lateinit var russianVoiceSpinner: Spinner
    private lateinit var speedSpinner: Spinner
    private lateinit var voiceInfo: TextView
    private lateinit var deviceInfo: TextView
    private val vosk by lazy { OfflineVoskEngine(this) }
    private fun packagedOfflineVoice(): Boolean =
        OfflineVoskEngine.packaged(this, "ru") &&
            OfflineVoskEngine.packaged(this, "en")
    private var direction = 0
    private var method = 0
    private var questionDirection = 0
    private var sessionSize = 50
    private val dictionarySettings by lazy { OfflineDictionarySettings.load(assets) }
    private val blockSizes = OfflineDictionarySettings.blockSizes
    private val pronunciationModes = OfflineDictionarySettings.pronunciationModes
    private var pronunciationMode = "original"
    private lateinit var topicSpinner: Spinner
    private lateinit var levelSpinner: Spinner
    private var topics: List<String> = emptyList()
    private var levels: List<String> = emptyList()
    private var trackPriority = false
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
    // Premium dark navy is the default; the user can choose light or system.
    // 0 = light, 1 = dark, 2 = system.
    private var themeChoice = 1
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
    private val navy: Int get() = if (isDarkTheme) Color.rgb(241, 235, 225) else Color.rgb(27, 42, 57)
    private val muted: Int get() = if (isDarkTheme) Color.rgb(191, 183, 171) else Color.rgb(102, 113, 125)
    private val blue: Int get() = if (isDarkTheme) Color.rgb(210, 181, 138) else Color.rgb(34, 53, 72)
    private val bg: Int get() = if (isDarkTheme) Color.rgb(11, 24, 39) else Color.rgb(241, 245, 249)
    private val white: Int get() = if (isDarkTheme) Color.rgb(26, 43, 61) else Color.WHITE
    private val neutralSurface: Int get() = if (isDarkTheme) Color.rgb(38, 55, 73) else Color.rgb(226, 233, 239)
    private val neutralInk: Int get() = if (isDarkTheme) Color.rgb(235, 238, 242) else Color.rgb(38, 45, 52)
    private lateinit var menu: LinearLayout
    private lateinit var menuButton: Button
    private lateinit var appHeader: LinearLayout
    private enum class Page { HOME, LEARN, CHECK, SETTINGS }
    private var page = Page.HOME
    private var returnFromSettings = Page.HOME
    private var loadedMode: Page? = null
    private lateinit var pageScroll: ScrollView
    private lateinit var pageTitle: TextView
    private lateinit var homePage: LinearLayout
    private lateinit var exercisePage: LinearLayout
    private lateinit var exerciseBody: LinearLayout
    private lateinit var checkControls: LinearLayout
    private lateinit var learnControls: LinearLayout
    private lateinit var startButton: Button
    private lateinit var writtenAnswer: EditText
    private lateinit var answerTimerLabel: TextView
    private var answerCountdown: android.os.CountDownTimer? = null
    private var checkPaused = false
    private var skippedInBlock = emptySet<Int>()
    private var lessonSound = true
    private var manualLearning = false
    private val accent = Color.rgb(255, 199, 67)
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
        themeChoice = prefs.getInt("ui_theme", 1).coerceIn(0, 2)
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
            val ru = w.getString("ru")
            val grammar = w.optJSONObject("grammar")
            words.add(Word(
                w.getInt("id"), w.getString("en"), ru, w.optString("pron"),
                w.optInt("order", i + 1), w.optString("primaryRu", ru),
                w.optString("ipaUs"), w.optString("ipaUk"),
                w.optString("topic"), w.optString("level"),
                w.optBoolean("learnEnabled", true), w.optBoolean("testEnabled", true),
                w.dictionaryList("acceptedRu"), w.dictionaryList("acceptedEn"), w.dictionaryList("examplesEn"),
                w.dictionaryList("family"), w.dictionaryList("collocations"), w.dictionaryList("phrasalVerbs"),
                grammar?.let { g -> g.keys().asSequence().associateWith { g.getString(it) } }.orEmpty(),
            ))
        }
        words.sortBy { it.order }
        topics = words.map { it.topic }.filter { it.isNotBlank() }.distinct().sorted()
        levels = words.map { it.level }.filter { it.isNotBlank() }.distinct()
        pronunciationMode = prefs.getString("dictionary_pronunciation", null)
            ?.takeIf { it in pronunciationModes } ?: dictionarySettings.defaultPronunciation
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
        voiceResponseSeconds = prefs.getInt("voice_response_seconds", 4)
            .takeIf { it in voiceResponseOptions } ?: 4
        tts = TextToSpeech(this, this)
        bundledSpeech.prepare { ready, _ -> bundledSpeechReady = ready }
        uiEnglish = prefs.getString("ui_language", "ru") == "en"
        fontChoice = prefs.getInt("font_family", 1)
        weightChoice = prefs.getInt("font_weight", 1)
        writingChoice = prefs.getInt("writing_style", 0)
        buildUi()
        // Prefer Android's saved state on rotation; use durable snapshot after process death.
        lessonSound = prefs.getBoolean("lesson_sound", true)
        manualLearning = prefs.getBoolean("lesson_manual", false)
        val restoredPage = savedInstanceState?.getString("screen")
            ?.let { value -> Page.entries.firstOrNull { it.name == value } } ?: Page.HOME
        returnFromSettings = savedInstanceState?.getString("settings_return")
            ?.let { value -> Page.entries.firstOrNull { it.name == value } } ?: Page.HOME
        when (restoredPage) {
            Page.LEARN, Page.CHECK -> openExercise(restoredPage, savedInstanceState)
            else -> displayPage(restoredPage)
        }
        refresh()
        handleExternalEntry(intent)
        handleReminderEntry(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            setIntent(intent)
            handleExternalEntry(intent)
            handleReminderEntry(intent)
        }
    }

    private fun handleExternalEntry(intent: Intent?) {
        if (intent?.getBooleanExtra("open_settings", false) == true) {
            intent.removeExtra("open_settings")
            openSettings()
        }
        if (intent?.getBooleanExtra("open_home", false) == true) {
            intent.removeExtra("open_home")
            displayPage(Page.HOME)
        }
        if (intent?.getBooleanExtra("open_check", false) == true) {
            intent.removeExtra("open_check")
            openExercise(Page.CHECK)
            selectionSpinner.setSelection(1)
        }
        if (intent?.getBooleanExtra("open_learn", false) == true) {
            intent.removeExtra("open_learn")
            openExercise(Page.LEARN)
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
        if (requestCode == acousticPermissionRequestCode) {
            if (automaticAudioCheckRunning) {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) runAutomaticAcousticCheck()
                else {
                    automaticAudioCheckRunning = false
                    automaticAudioCheckDone = true
                    resumeLearningWord()
                }
            }
        }
        if (requestCode == 12) {
            status.text = if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                if (uiEnglish) "Tap the microphone to answer" else "Нажмите микрофон для ответа"
            } else {
                if (uiEnglish) "Microphone denied. Text input is available."
                else "Микрофон не разрешён. Можно ответить текстом."
            }
        }
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
            text = tr(value)
            textSize = 15f
            isAllCaps = false
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            letterSpacing = 0.015f
            setTextColor(ink)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(color, color)
            ).apply {
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), if (isDarkTheme) Color.rgb(112, 100, 83)
                    else Color.rgb(205, 193, 177))
            }
            minimumHeight = dp(48)
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
    private fun dictionaryFilter(options: List<String>, key: String, allLabel: String): Spinner =
        spinner(listOf(allLabel) + options).apply {
            val saved = prefs.getString(key, null)
            setSelection(saved?.let { options.indexOf(it) + 1 } ?: 0)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    prefs.edit().putString(key, options.getOrNull(position - 1)).apply()
                }
            }
        }

    private fun setting(parent: LinearLayout, title: String, spinner: Spinner) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(text(title, 14f, muted).apply { gravity = Gravity.START },
            LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(spinner, LinearLayout.LayoutParams(0, dp(44), 1.15f))
        add(parent, row)
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply { setFillViewport(true); setBackgroundColor(bg) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val wide = resources.configuration.screenWidthDp >= 600
            setPadding(dp(if (wide) 24 else 14), dp(8), dp(if (wide) 24 else 14), dp(12))
        }
        scroll.addView(root)
        pageScroll = scroll
        setContentView(scroll)
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            scroll.setOnApplyWindowInsetsListener { view, insets ->
                val safeArea = insets.getInsets(android.view.WindowInsets.Type.systemBars() or
                    android.view.WindowInsets.Type.displayCutout())
                view.setPadding(safeArea.left, safeArea.top, safeArea.right, safeArea.bottom)
                insets
            }
        }
        appearanceRoot = root

        // Premium home navigation: exactly three primary actions, no separate offline mode.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(2), dp(8), dp(3))
        }
        appHeader = header
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = true
            setOnClickListener { displayPage(Page.HOME) }
            setOnLongClickListener { openSettings(); true }
            contentDescription = "English Words. Удерживайте для настроек"
        }
        add(brand, text("English Words", 22f, navy).apply {
            gravity = Gravity.START
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            letterSpacing = 0.015f
        })
        add(brand, text("LANGUAGE LEARNING", 8f, muted).apply {
            letterSpacing = 0.25f
        }, top = 3)
        header.addView(brand, LinearLayout.LayoutParams(0, dp(38), 1f))
        menuButton = button("⚙", neutralSurface, neutralInk) { openSettings() }.apply {
            textSize = 18f
            contentDescription = if (uiEnglish) "Settings" else "Настройки"
        }
        header.addView(menuButton, LinearLayout.LayoutParams(dp(36), dp(36)))
        add(root, header)
        val headerDivider = View(this).apply { setBackgroundColor(Color.rgb(210, 167, 80)) }
        add(root, headerDivider)
        headerDivider.layoutParams = LinearLayout.LayoutParams(-1, dp(1))
        pageTitle = text("", 23f, navy, true).apply {
            setPadding(0, 0, 0, dp(8))
            setOnClickListener { navigateBack() }
        }
        add(root, pageTitle)
        homePage = column()
        exercisePage = column()
        exerciseBody = column()
        checkControls = column()
        learnControls = column()
        add(root, homePage)
        add(root, exercisePage)

        // Approved P01 visual baseline, 2026-11-16 v1.0 CURRENT:
        // Greeting and primary navigation belong on the home screen.
        val homeGreeting = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(2), dp(12), dp(2))
        }
        add(homeGreeting, text("Welcome!", 31f, Color.rgb(255, 211, 92), bold = true).apply {
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        add(homeGreeting, text("Что вы хотите делать сегодня?", 19f,
            if (isDarkTheme) Color.WHITE else navy, bold = true).apply {
            gravity = Gravity.CENTER
            maxLines = 3
        }, top = 6)
        add(homePage, homeGreeting, top = 2)

        fun premiumAction(title: String, symbol: String, action: () -> Unit): LinearLayout {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.rgb(34, 53, 72), Color.rgb(15, 28, 43))
                ).apply {
                    cornerRadius = dp(22).toFloat()
                    setStroke(dp(3), Color.rgb(255, 195, 62))
                }
                setPadding(dp(14), dp(6), dp(12), dp(6))
                isClickable = true
                isFocusable = true
                // Preserve accessible aliases used by existing S20 device-test scripts,
                // without changing the premium Russian titles visible to learners.
                contentDescription = when (title) {
                    "Перевести слово" -> "Спроси слово · $title"
                    "Учить слова" -> "Изучать · $title"
                    "Проверка знаний" -> "Проверять · $title"
                    else -> title
                }
                setOnClickListener { action() }
                elevation = dp(3).toFloat()
            }
            card.addView(text(symbol, 29f, Color.rgb(255, 203, 75)).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(44), dp(42)))
            card.addView(text(title, 23f, Color.WHITE, bold = true).apply {
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                gravity = Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(8) })
            card.addView(text("›", 30f, Color.rgb(214, 187, 146)).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(24), dp(42)))
            return card
        }
        val modeActions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        add(modeActions, premiumAction("Перевести слово", "A ⇄ a") {
            showAskWordOptions()
        }, top = 6)
        add(modeActions, premiumAction("Учить слова", "▤") {
            openExercise(Page.LEARN)
        }, top = 8)
        add(modeActions, premiumAction("Проверка знаний", "✓") {
            openExercise(Page.CHECK)
        }, top = 8)
        add(homePage, modeActions, top = 6)

        menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(18))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(if (isDarkTheme) Color.rgb(26, 39, 54) else Color.rgb(247, 248, 250),
                    if (isDarkTheme) Color.rgb(15, 27, 41) else Color.rgb(234, 239, 244))
            ).apply {
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.rgb(160, 137, 103))
            }
            visibility = View.GONE
        }
        add(root, menu)
        exerciseOptions = column().apply {
            background = bordered(neutralSurface)
            setPadding(dp(10), dp(7), dp(10), dp(7))
        }
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
        blockSpinner = spinner(blockSizes.map { if (uiEnglish) "$it words" else "$it слов" }).apply {
            setSelection(blockSizes.indexOf(dictionarySettings.defaultBlockSize))
        }
        topicSpinner = dictionaryFilter(topics, "dictionary_topic", if (uiEnglish) "All topics" else "Все темы")
        levelSpinner = dictionaryFilter(levels, "dictionary_level", if (uiEnglish) "All levels" else "Все уровни")
        val pronunciationOptions = if (uiEnglish) listOf("IPA US", "IPA UK", "Original hint")
            else listOf("IPA США", "IPA Великобритания", "Исходная подсказка")
        val pronunciationSpinner = spinner(pronunciationOptions).apply {
            setSelection(pronunciationModes.indexOf(pronunciationMode))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val chosen = pronunciationModes[position]
                    if (chosen == pronunciationMode) return
                    pronunciationMode = chosen
                    prefs.edit().putString("dictionary_pronunciation", chosen).apply()
                    if (::pronunciation.isInitialized) current?.let { word ->
                        if (answering) pronunciation.text = "${word.en}  •  ${displayPronunciation(word)}"
                        else if (learningMode || questionDirection == 0) pronunciation.text = displayPronunciation(word)
                    }
                }
            }
        }
        setting(exerciseOptions, if (uiEnglish) "Dictionary topic" else "Тема словаря", topicSpinner)
        setting(exerciseOptions, if (uiEnglish) "Word level" else "Уровень слов", levelSpinner)
        setting(menu, if (uiEnglish) "Pronunciation format" else "Формат произношения", pronunciationSpinner)
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
        setting(learnControls, if (uiEnglish) "Location / scene" else "Где я нахожусь", sceneSpinner)
        setting(learnControls, if (uiEnglish) "Pause after translation" else
            "Пауза после перевода (изучение)", learningPauseSpinner)
        setting(exerciseOptions, "Направление перевода", dirSpinner)
        setting(checkControls, "Способ ответа", methodSpinner)
        val responseTimeSpinner = spinner(voiceResponseOptions.map {
            if (uiEnglish) "$it s" else "$it с"
        }).apply {
            setSelection(voiceResponseOptions.indexOf(voiceResponseSeconds))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    voiceResponseSeconds = voiceResponseOptions[position]
                    prefs.edit().putInt("voice_response_seconds", voiceResponseSeconds).apply()
                }
            }
        }
        setting(checkControls, if (uiEnglish) "Voice response time" else "Время голосового ответа",
            responseTimeSpinner)
        setting(exerciseOptions, "Размер блока", blockSpinner)
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
        setting(checkControls, if (uiEnglish) "Next word" else "Переход к следующему слову", transitionSpinner)
        setting(exerciseOptions, "Какие слова повторять", selectionSpinner)
        add(exerciseOptions, learnControls)
        add(exerciseOptions, checkControls)
        add(learnControls, CheckBox(this).apply {
            text = if (uiEnglish) "Read examples aloud" else "Озвучивать примеры"
            isChecked = ExampleModeSetup.selected(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                ExampleModeSetup.save(this@MainActivity, checked)
                includeExamplesDuringLearning = checked
                if (running) persistActiveSession()
            }
        })
        add(learnControls, CheckBox(this).apply {
            text = if (uiEnglish) "Advance manually" else "Листать слова вручную"
            isChecked = prefs.getBoolean("lesson_manual", false)
            setOnCheckedChangeListener { _, checked ->
                manualLearning = checked
                prefs.edit().putBoolean("lesson_manual", checked).apply()
                handler.removeCallbacks(learningAdvanceRunnable)
                if (!checked && running && lessonSound) resumeLearningWord()
            }
        })
        startButton = button("Начать", accent, Color.rgb(7, 28, 50)) {
            startSession(modeOverride = if (page == Page.LEARN) "learn" else "check")
        }
        add(exercisePage, startButton, top = 10)
        add(exercisePage, exerciseBody, top = 8)
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
        add(menu, button(if (uiEnglish) "My dictionary and history" else "Мой словарь и история",
            neutralSurface, neutralInk) {
            startActivity(Intent(this, VocabularyHistoryActivity::class.java))
        }, 50, 8)
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
        add(menu, button("Установить офлайн-голоса EN / RU", neutralSurface, blue) {
            runCatching {
                startActivity(Intent("com.android.settings.TTS_SETTINGS"))
            }.onFailure {
                runCatching { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }
            }
        }, top = 8)
        add(menu, button("Обновить список офлайн-голосов", neutralSurface, blue) {
            if (::tts.isInitialized && ttsReady) {
                englishOfflineVoices = offlineVoicesFor(Locale.US)
                russianOfflineVoices = offlineVoicesFor(Locale("ru", "RU"))
                updateOfflineVoiceControls()
                Toast.makeText(this, "EN: ${englishOfflineVoices.size}; RU: ${russianOfflineVoices.size}", Toast.LENGTH_LONG).show()
            }
        }, top = 8)
        add(menu, button("▶ Прослушать английский голос", white, navy) {
            say("Hello! Welcome to English Words.", Locale.US)
        }, 50, 8)
        add(menu, button("▶ Прослушать русский голос", white, navy) {
            say("Привет! Проверяем произношение.", Locale("ru", "RU"))
        }, 50, 6)
        if (ttsReady) updateOfflineVoiceControls()
        menu.addView(CheckBox(this).apply {
            text = if (uiEnglish) "Show word illustrations" else "Показывать иллюстрации"
            isChecked = prefs.getBoolean("show_illustrations", true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("show_illustrations", checked).apply()
                updateWordImage(current, learningMode || answering)
            }
        })
        add(menu, button(if (uiEnglish) "Reset appearance" else "Сбросить оформление") {
            android.app.AlertDialog.Builder(this)
                .setMessage(if (uiEnglish) "Restore default appearance?" else "Вернуть оформление по умолчанию?")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    prefs.edit().remove("ui_theme").remove("ui_language")
                        .remove("font_family").remove("font_weight").remove("writing_style").apply()
                    recreate()
                }.show()
        }, top = 12)
        add(menu, button(if (uiEnglish) "Save and go back" else "Сохранить и вернуться") {
            navigateBack()
        }, top = 8)
        add(menu, button(if (uiEnglish) "Home" else "Главное меню") { displayPage(Page.HOME) }, top = 8)

        counter = text("Выбери блок в меню", 16f, navy, true)
        add(exerciseBody, counter, top = 24)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = 0; progressTintList = android.content.res.ColorStateList.valueOf(blue)
        }
        add(exerciseBody, progress, 6, 12)
        answerTimerLabel = text("", 14f, accent).apply { visibility = View.GONE }
        add(exerciseBody, answerTimerLabel, top = 5)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(12), dp(15), dp(12), dp(15))
            background = bordered(white)
            minimumHeight = dp(205)
        }
        wordCard = card
        // The three-line menu belongs INSIDE the word card, never above it.
        embeddedOptionsButton = button("☰", neutralSurface, neutralInk) {
            embeddedOptionsExpanded = !embeddedOptionsExpanded
            exerciseOptions.visibility = if (embeddedOptionsExpanded) View.VISIBLE else View.GONE
            embeddedOptionsButton.contentDescription = if (embeddedOptionsExpanded)
                "Скрыть параметры урока" else "Показать параметры урока"
        }.apply {
            textSize = 22f
            contentDescription = "Показать параметры урока"
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        add(card, embeddedOptionsButton)
        exerciseOptions.visibility = View.GONE
        add(card, exerciseOptions, top = 2)
        buildLessonToolbar(card)
        cardHeading = text(if (uiEnglish) "TRANSLATE" else "ПЕРЕВОД", 12f, muted, true)
        add(card, cardHeading)
        prompt = text(if (uiEnglish) "Open ⋮ and start a lesson" else
            "Открой ⋮ и начни урок", 37f, navy, true).apply {
            setPadding(dp(2), dp(26), dp(2), dp(14))
        }
        add(card, prompt)
        wordImage = ImageView(this).apply {
            visibility = View.GONE
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            contentDescription = ""
        }
        card.addView(wordImage, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(265)
        ).apply { topMargin = dp(8); bottomMargin = dp(8) })
        translationText = text("", 28f, navy, true).apply {
            gravity = Gravity.CENTER; visibility = View.GONE
            setPadding(dp(8), dp(3), dp(8), dp(6))
        }
        pronunciation = text("", 29f, blue, true).apply { setPadding(dp(2), dp(4), dp(2), dp(10)) }
        add(card, pronunciation)
        add(card, translationText)
        buildFinishedLessonPanel(card)
        add(exerciseBody, card, top = 18)
        examplesToggle = button(if (uiEnglish) "Everyday phrases  ▾" else "Примеры из жизни  ▾", white, blue) {
            toggleExamples()
        }.apply { visibility = View.GONE }
        add(exerciseBody, examplesToggle, 52, 12)
        familyButton = button(if (uiEnglish) "Word family  ▾" else "Семья слова  ▾",
            white, navy) {
            openFamily()
        }.apply { visibility = View.GONE }
        add(exerciseBody, familyButton, 52, 8)
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
        add(exerciseBody, status)

        listen = button(if (uiEnglish) "🎤 Speak" else "🎤 Ответить", navy) { listenNow() }
        add(exerciseBody, listen, 52, 8)
        writtenAnswer = EditText(this).apply {
            setOnTouchListener { _, event ->
                if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                    vosk.stop()
                    status.text = if (uiEnglish) "Type your answer" else "Введите перевод"
                }
                false
            }
            hint = if (uiEnglish) "Enter your answer" else "Введите перевод"
            setSingleLine(true)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { _, action, _ ->
                if (action != android.view.inputmethod.EditorInfo.IME_ACTION_DONE) false
                else { submitWrittenAnswer(); true }
            }
        }
        add(exerciseBody, writtenAnswer, top = 8)
        add(exerciseBody, button(if (uiEnglish) "Check answer" else "Проверить ответ") {
            submitWrittenAnswer()
        }.apply { tag = "typed_answer" }, top = 5)
        repeat = button("Слушать слово", white, blue) {
            current?.let { word ->
                vosk.stop()
                clearAnswerTimer()
                say(question(word), questionLocale()) {
                    if (running && current?.id == word.id && answering) scheduleAdvance()
                }
            }
        }
        add(exerciseBody, repeat, 56, 12)
        learningPauseButton = button(if (uiEnglish) "Pause listening" else "Пауза",
            neutralSurface, neutralInk) {
            if (running && !learningMode) {
                checkPaused = !checkPaused
                if (checkPaused) stopExerciseWork() else scheduleAdvance()
                updateButtons()
            } else if (running) {
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
        val navigation = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        navigation.addView(button(if (uiEnglish) "Previous" else "Назад", neutralSurface, neutralInk) {
            if (running && index > 0 && (learningMode || !checkPaused)) {
                index--
                next(savedDirection = forcedReviewDirections[reviewIds[index]])
            }
        }, LinearLayout.LayoutParams(0, dp(50), 1f))
        navigation.addView(learningPauseButton, LinearLayout.LayoutParams(0, dp(50), 1f))
        navigation.tag = "exercise_navigation"
        val rating = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        ratingRow = rating
        correct = button("Знаю", blue) { grade(true, "Отмечено: знаю") }
        incorrect = button("Не знаю", neutralSurface, neutralInk) { grade(false, "Отмечено: не знаю") }
        rating.addView(correct, LinearLayout.LayoutParams(0, dp(58), 1f).apply { rightMargin = dp(6) })
        rating.addView(incorrect, LinearLayout.LayoutParams(0, dp(58), 1f).apply { leftMargin = dp(6) })
        add(exerciseBody, rating, top = 12)
        skip = button("Пропустить", white, muted) {
            grade(false, "Пропущено", skipped = true)
        }
        add(exerciseBody, skip, 50, 12)
        nextWordButton = button(if (uiEnglish) "Next →" else "Вперёд →") {
            if (running && learningMode) {
                pauseLearningAudio()
                index++
                next()
            } else if (running && answering) advanceToNext()
        }.apply { visibility = View.GONE }
        navigation.addView(nextWordButton, LinearLayout.LayoutParams(0, dp(50), 1f))
        add(exerciseBody, navigation, top = 8)
        stats = text("", 14f, muted)
        add(exerciseBody, stats, top = 20)
        updateButtons()
        // Keep exactly the phone's single-column screen on an unfolded Fold.
        // The scroll container expands with the window; do not split the current
        // lesson or show parallel navigation, testing and statistics panels.
        root.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
            val isWide = (right - left) / resources.displayMetrics.density >= 600f
            val horizontalPadding = dp(if (isWide) 32 else 14)
            if (root.paddingLeft != horizontalPadding) {
                root.setPadding(horizontalPadding, root.paddingTop,
                    horizontalPadding, root.paddingBottom)
            }
        }
        applyTypography(root)
    }

    private fun buildLessonToolbar(card: LinearLayout) {
        lessonToolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        previousLesson = lessonArrow("←", if (uiEnglish) "Previous lesson" else "Предыдущий урок", -1)
        nextLesson = lessonArrow("→", if (uiEnglish) "Next lesson" else "Следующий урок", 1)
        lessonNumber = lessonToolbarLabel()
        lessonWordPosition = lessonToolbarLabel()
        lessonToolbar.addView(previousLesson, LinearLayout.LayoutParams(dp(40), dp(44)))
        lessonToolbar.addView(lessonNumber, LinearLayout.LayoutParams(0, dp(44), 1f))
        lessonToolbar.addView(nextLesson, LinearLayout.LayoutParams(dp(40), dp(44)))
        lessonToolbar.addView(lessonWordPosition, LinearLayout.LayoutParams(0, dp(44), 1.1f).apply {
            marginStart = dp(6)
            marginEnd = dp(6)
        })
        lessonSpeaker = ImageButton(this).apply {
            background = rounded(neutralSurface, 12)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            scaleType = ImageView.ScaleType.FIT_CENTER
            imageTintList = android.content.res.ColorStateList.valueOf(blue)
            setOnClickListener {
                if (running) {
                    lessonSound = !lessonSound
                    prefs.edit().putBoolean("lesson_sound", lessonSound).apply()
                    if (lessonSound) narrateLearningWord() else pauseLearningAudio()
                    updateButtons()
                }
            }
        }
        lessonToolbar.addView(lessonSpeaker, LinearLayout.LayoutParams(dp(40), dp(44)))
        add(card, lessonToolbar)
    }

    private fun lessonArrow(symbol: String, label: String, offset: Int): Button =
        button(symbol, neutralSurface, blue) { moveLearningLesson(offset) }.apply {
            contentDescription = label
            setPadding(0, 0, 0, 0)
            minimumWidth = 0
            minWidth = 0
            textSize = 19f
        }

    private fun lessonToolbarLabel(): TextView = text("", 14f, navy, true).apply {
        maxLines = 1
        setAutoSizeTextTypeUniformWithConfiguration(10, 14, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
    }

    private fun showLessonMenu() {
        // Open the existing settings, not a second lesson-selection/progress popup.
        // The lesson number and arrows already control lesson navigation in the toolbar.
        openSettings()
    }

    private fun buildFinishedLessonPanel(card: LinearLayout) {
        finishedLessonPanel = column().apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(22), 0, dp(22))
            visibility = View.GONE
        }
        add(finishedLessonPanel, text(if (uiEnglish) "Lesson finished" else "Урок закончился",
            22f, navy, true))
        val selector = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        finishedLessonBack = button("←", neutralSurface, blue) { changeFinishedLesson(-1) }
        finishedLessonForward = button("→", neutralSurface, blue) { changeFinishedLesson(1) }
        finishedLessonBack.contentDescription = if (uiEnglish) "Select previous lesson"
            else "Выбрать предыдущий урок"
        finishedLessonForward.contentDescription = if (uiEnglish) "Select next lesson"
            else "Выбрать следующий урок"
        finishedLessonNumber = button("", blue, Color.rgb(11, 24, 39)) {
            finishedLessonSelection?.let { openLearningLesson(it.index) }
        }.apply {
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 0)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(blue)
            }
        }
        for (arrow in listOf(finishedLessonBack, finishedLessonForward)) {
            arrow.setPadding(0, 0, 0, 0)
            arrow.textSize = 22f
        }
        selector.addView(finishedLessonBack, LinearLayout.LayoutParams(dp(52), dp(48)))
        selector.addView(finishedLessonNumber, LinearLayout.LayoutParams(dp(62), dp(62)).apply {
            marginStart = dp(20)
            marginEnd = dp(20)
        })
        selector.addView(finishedLessonForward, LinearLayout.LayoutParams(dp(52), dp(48)))
        add(finishedLessonPanel, selector, top = 22)
        add(card, finishedLessonPanel)
    }

    private fun changeFinishedLesson(offset: Int) {
        val selection = finishedLessonSelection ?: return
        selection.move(offset)
        refreshFinishedLessonSelector()
        persistActiveSession()
    }

    private fun refreshFinishedLessonSelector() {
        val selection = finishedLessonSelection ?: return
        val number = selection.index + 1
        finishedLessonNumber.text = number.toString()
        finishedLessonNumber.contentDescription = if (uiEnglish) "Open lesson $number"
            else "Открыть урок $number"
        finishedLessonBack.isEnabled = selection.canMove(-1)
        finishedLessonForward.isEnabled = selection.canMove(1)
        finishedLessonBack.alpha = if (finishedLessonBack.isEnabled) 1f else .38f
        finishedLessonForward.alpha = if (finishedLessonForward.isEnabled) 1f else .38f
    }

    private fun moveLearningLesson(offset: Int) {
        val plan = learningPlan ?: return
        openLearningLesson(plan.current + offset)
    }

    private fun openLearningLesson(target: Int) {
        val plan = learningPlan ?: return
        if (!learningMode || page != Page.LEARN || target !in plan.lessons.indices) return
        if (finishedLessonSelection == null) plan.rememberPosition(index)
        else learningPaused = false
        stopExerciseWork()
        examplesDialog?.dismiss()
        familyDialog?.dismiss()
        finishedLessonSelection = null
        plan.current = target
        reviewIds = plan.words
        originalBlockIds = reviewIds
        index = plan.position
        sessionId = UUID.randomUUID().toString()
        skippedInBlock = emptySet()
        running = true
        answering = false
        next()
    }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun bordered(fill: Int) = rounded(fill, 16).apply { setStroke(dp(2), accent) }

    private fun displayPage(destination: Page) {
        if (page != destination && (page == Page.LEARN || page == Page.CHECK)) {
            stopExerciseWork()
            persistActiveSession(commitNow = true)
        }
        page = destination
        if (destination == Page.LEARN || destination == Page.CHECK) {
            embeddedOptionsExpanded = false
        }
        appHeader.visibility = if (page == Page.HOME) View.VISIBLE else View.GONE
        homePage.visibility = if (page == Page.HOME) View.VISIBLE else View.GONE
        menu.visibility = if (page == Page.SETTINGS) View.VISIBLE else View.GONE
        exercisePage.visibility = if (page == Page.LEARN || page == Page.CHECK) View.VISIBLE else View.GONE
        pageTitle.visibility = if (page == Page.HOME) View.GONE else View.VISIBLE
        pageTitle.text = when (page) {
            Page.HOME -> ""
            Page.LEARN -> if (uiEnglish) "‹   Learn words" else "‹   Учить слова"
            Page.CHECK -> if (uiEnglish) "‹   Knowledge check" else "‹   Проверка знаний"
            Page.SETTINGS -> if (uiEnglish) "‹   Settings" else "‹   Настройки приложения"
        }
        learnControls.visibility = if (page == Page.LEARN) View.VISIBLE else View.GONE
        checkControls.visibility = if (page == Page.CHECK) View.VISIBLE else View.GONE
        startButton.text = if (page == Page.LEARN) {
            if (uiEnglish) "Start learning" else "Начать обучение"
        } else if (uiEnglish) "Start check" else "Начать проверку"
        pageScroll.scrollTo(0, 0)
    }

    private fun openExercise(destination: Page, savedState: Bundle? = null) {
        displayPage(destination)
        val sameMode = loadedMode == destination
        if (!sameMode) {
            loadedMode = destination
            stopExerciseWork()
            running = false
            answering = false
            checkPaused = false
            current = null
            learningPlan = null
            finishedLessonSelection = null
            progress.progress = 0
            pendingBlockErrors = emptyMap()
            learningMode = destination == Page.LEARN
            val restored = savedState?.takeIf { it.containsKey("lesson_ids") }
                ?: readPersistedSession(learningMode)
            restoreSession(restored)
        } else if (learningMode) {
            resumeLearningWord()
        } else {
            scheduleAdvance()
        }
        updateButtons()
    }

    private fun runAutomaticAcousticCheck() {
        AcousticLoopbackCheck.run(this) { result ->
            runOnUiThread {
                automaticAudioCheckRunning = false
                automaticAudioCheckDone = true
                if (!result.passed) {
                    status.text = "Проверка звука: ${result.code}. Восстановление голоса…"
                    if (!automaticAudioRepairAttempted &&
                        result.code != "MUTED" && result.code != "EXTERNAL_AUDIO" &&
                        result.code != "PERMISSION_DENIED") {
                        automaticAudioRepairAttempted = true
                        bundledSpeech.prepare { ready, message ->
                            bundledSpeechReady = ready
                            runOnUiThread {
                                if (!ready) status.text = "Офлайн-голос: $message"
                                resumeLearningWord()
                            }
                        }
                        return@runOnUiThread
                    }
                }
                resumeLearningWord()
            }
        }
    }

    private fun openSettings() {
        if (page == Page.SETTINGS) return
        returnFromSettings = page
        displayPage(Page.SETTINGS)
    }

    private fun stopExerciseWork() {
        clearAnswerTimer()
        pauseLearningAudio()
        tts.stop()
        vosk.stop()
    }

    private fun navigateBack() {
        if (page == Page.SETTINGS && returnFromSettings in setOf(Page.LEARN, Page.CHECK)) {
            openExercise(returnFromSettings)
        } else {
            displayPage(Page.HOME)
        }
    }

    @Deprecated("Use Android's back dispatcher when migrating this Activity.")
    override fun onBackPressed() {
        if (page == Page.HOME) super.onBackPressed() else navigateBack()
    }

    private fun submitWrittenAnswer() {
        val word = current ?: return
        val value = writtenAnswer.text.toString().trim()
        if (page != Page.CHECK || !running || answering || checkPaused || value.isEmpty()) return
        vosk.stop()
        grade(isCorrect(value, word), value)
    }

    private fun updateDeviceCapabilities() {
        if (!::deviceInfo.isInitialized) return
        val recognition = if (packagedOfflineVoice()) {
            "Vosk: встроенные английская и русская модели — без интернета"
        } else {
            "Модели Vosk недоступны. Можно отвечать текстом или кнопками."
        }
        deviceInfo.text = "Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}\n" +
            recognition + "\nИнтерфейс и словарь доступны без распознавания речи."
    }

    private fun showOverflowMenu() {
        val popup = PopupMenu(this, menuButton)
        popup.menu.add(0, 1, 0, if (uiEnglish) "Appearance and settings" else "Оформление и настройки")
        popup.menu.add(0, 3, 2, if (uiEnglish) "About the app" else "О приложении")
        popup.menu.add(0, 4, 3, if (uiEnglish) "My dictionary and history" else "Мой словарь и история")
        popup.menu.add(0, 5, 4, if (uiEnglish) "Review due words" else "Что повторить")
        popup.menu.add(0, 6, 5, if (uiEnglish) "Extra photo phrases" else "Дополнительные фотографии")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> { openSettings(); true }
                3 -> {
                    android.app.AlertDialog.Builder(this)
                        .setTitle("English Words")
                        .setMessage(if (uiEnglish)
                            "Offline vocabulary practice"
                            else "Тренировка слов без интернета")
                        .setPositiveButton("OK", null).show()
                    true
                }
                4 -> { startActivity(Intent(this, VocabularyHistoryActivity::class.java)); true }
                5 -> { showReviewRecommendations(); true }
                6 -> { startActivity(Intent(this, SupplementalPhotosActivity::class.java)); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun showLearningOptions(fromReminder: Boolean = false) {
        openExercise(Page.LEARN)
        if (fromReminder) startSession(modeOverride = "learn", fromReminder = true)
    }

    private fun showAskWordOptions() {
        startActivity(Intent(this, AskWordActivity::class.java))
    }

    private fun updateButtons() {
        val testing = !learningMode
        val finished = !testing && finishedLessonSelection != null
        finishedLessonPanel.visibility = if (finished) View.VISIBLE else View.GONE
        prompt.visibility = if (finished) View.GONE else View.VISIBLE
        pronunciation.visibility = prompt.visibility
        status.visibility = prompt.visibility
        stats.visibility = prompt.visibility
        exerciseOptions.visibility = if (finished || !embeddedOptionsExpanded) View.GONE else View.VISIBLE
        embeddedOptionsButton.visibility = if (finished) View.GONE else View.VISIBLE
        startButton.visibility = prompt.visibility
        exerciseBody.findViewWithTag<View>("exercise_navigation").visibility = prompt.visibility
        if (finished) {
            translationText.visibility = View.GONE
            refreshFinishedLessonSelector()
        }
        cardHeading.visibility = if (testing) View.VISIBLE else View.GONE
        counter.visibility = cardHeading.visibility
        progress.visibility = cardHeading.visibility
        lessonToolbar.visibility = if (testing || finished) View.GONE else View.VISIBLE
        val cardLayout = wordCard.layoutParams as LinearLayout.LayoutParams
        val cardTop = dp(0)
        if (cardLayout.topMargin != cardTop) {
            cardLayout.topMargin = cardTop
            wordCard.layoutParams = cardLayout
        }
        if (!testing) {
            val plan = learningPlan
            lessonNumber.text = if (uiEnglish) "Lesson ${(plan?.current ?: 0) + 1}"
                else "Урок ${(plan?.current ?: 0) + 1}"
            val total = reviewIds.size
            val number = (index + 1).coerceAtMost(total)
            lessonWordPosition.text = if (uiEnglish) "$number of $total" else "$number из $total"
            previousLesson.isEnabled = plan?.canMove(-1) == true
            nextLesson.isEnabled = plan?.canMove(1) == true
            previousLesson.alpha = if (previousLesson.isEnabled) 1f else .38f
            nextLesson.alpha = if (nextLesson.isEnabled) 1f else .38f
            lessonSpeaker.isEnabled = running
            lessonSpeaker.setImageResource(if (lessonSound) android.R.drawable.ic_lock_silent_mode_off
                else android.R.drawable.ic_lock_silent_mode)
            lessonSpeaker.contentDescription = if (lessonSound) {
                if (uiEnglish) "Mute lesson audio" else "Выключить озвучивание"
            } else if (uiEnglish) "Unmute lesson audio" else "Включить озвучивание"
        }
        exerciseBody.visibility = if (running || current != null || progress.progress == 100)
            View.VISIBLE else View.GONE
        val allowText = testing && running && !answering && !checkPaused
        writtenAnswer.visibility = if (allowText) View.VISIBLE else View.GONE
        exerciseBody.findViewWithTag<View>("typed_answer").visibility =
            if (allowText) View.VISIBLE else View.GONE
        listen.visibility = if (testing && method == 0 && packagedOfflineVoice())
            View.VISIBLE else View.GONE
        ratingRow.visibility = if (testing) View.VISIBLE else View.GONE
        correct.isEnabled = running && testing && !answering && !checkPaused
        incorrect.isEnabled = running && testing && !answering && !checkPaused
        listen.isEnabled = running && testing && !answering && !checkPaused
        repeat.visibility = if (testing) View.VISIBLE else View.GONE
        repeat.isEnabled = running && testing && !answering && !checkPaused
        repeat.text = if (uiEnglish) "Listen to word" else "Слушать слово"
        learningPauseButton.visibility = if (running)
            View.VISIBLE else View.GONE
        learningPauseButton.text = if (if (learningMode) learningPaused else checkPaused) {
            if (uiEnglish) "Resume listening" else "Продолжить"
        } else {
            if (uiEnglish) "Pause listening" else "Пауза"
        }
        skip.visibility = if (testing) View.VISIBLE else View.GONE
        skip.isEnabled = running && testing && !answering && !checkPaused
        nextWordButton.visibility = if (running && (learningMode || answering && !checkPaused))
            View.VISIBLE else View.GONE
    }

    private fun startSession(
        modeOverride: String? = null,
        fromReminder: Boolean = false,
        abandonConfirmed: Boolean = false,
        includeExamplesOverride: Boolean? = null
    ) {
        val destination = when (modeOverride) {
            "learn" -> Page.LEARN
            "check" -> Page.CHECK
            else -> if (lessonModeSpinner.selectedItemPosition == 0) Page.LEARN else Page.CHECK
        }
        if (page != destination) openExercise(destination)
        checkPaused = false
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
        automaticAudioCheckDone = false
        automaticAudioCheckRunning = false
        automaticAudioRepairAttempted = false
        finishedLessonSelection = null
        handler.removeCallbacksAndMessages(null)
        pauseLearningAudio()
        examplesDialog?.dismiss()
        familyDialog?.dismiss()
        examplesExpanded = false
        familyExpanded = false
        tts.stop(); vosk.stop()
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
        skippedInBlock = emptySet()
        direction = dirSpinner.selectedItemPosition
        method = methodSpinner.selectedItemPosition
        if (!learningMode && method == 0 && !packagedOfflineVoice()) {
            method = 1
            methodSpinner.setSelection(1)
            status.text = "Автономное распознавание недоступно: включён режим ответа кнопками"
        }
        sessionSize = if (fromReminder) 10
            else blockSizes.getOrElse(blockSpinner.selectedItemPosition) { dictionarySettings.defaultBlockSize }
        selectionMode = selectionSpinner.selectedItemPosition
        activeSceneId = if (learningMode) scenes.getOrNull(sceneSpinner.selectedItemPosition - 1)?.id.orEmpty() else ""
        val candidateWords = exerciseCandidates()
        trackPriority = !fromReminder && selectionMode != 1 && selectionMode != 2
        val ordered = if (fromReminder) candidateWords.sortedBy { word ->
            val attempts = (0..1).map { prefs.getInt("${it}_${word.id}", -1) }
            if (learningMode) {
                if (attempts.all { it == -1 }) 0 else if (attempts.any { it == 0 }) 1 else 2
            } else {
                if (attempts.any { it == 0 }) 0 else if (attempts.any { it == -1 }) 1 else 2
            }
        } else if (trackPriority) {
            val completed = priorityCompleted()
            val remaining = candidateWords.filterNot { it.id.toString() in completed }
            if (remaining.isEmpty() && candidateWords.isNotEmpty()) {
                val scopeIds = candidateWords.map { it.id.toString() }.toSet()
                prefs.edit().putStringSet(priorityKey(), completed - scopeIds).apply()
                candidateWords
            } else remaining
        } else candidateWords
        learningPlan = if (learningMode) {
            val candidates = if (fromReminder) ordered else candidateWords
            val completed = if (trackPriority) priorityCompleted() else emptySet()
            LearningLessonPlan.create(candidates.map { it.id }, sessionSize, completed)
        } else null
        reviewIds = learningPlan?.words ?: ordered.take(sessionSize).map { it.id }
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
        next()
    }
    private fun exerciseCandidates(): List<Word> {
        val selectedScene = scenes.firstOrNull { it.id == activeSceneId }
        val topic = topics.getOrNull(topicSpinner.selectedItemPosition - 1)
        val level = levels.getOrNull(levelSpinner.selectedItemPosition - 1)
        return words.filter { w ->
            (if (learningMode) w.learnEnabled else w.testEnabled) &&
                (topic == null || w.topic == topic) && (level == null || w.level == level) &&
                (if (selectedScene != null) {
                    w.id in selectedScene.wordIds
                } else when (selectionMode) {
                    1 -> (0..1).any { prefs.getInt("${it}_${w.id}", -1) == 0 }
                    2 -> (0..1).all { prefs.getInt("${it}_${w.id}", -1) == -1 }
                    3 -> scenePhrases(w).isNotEmpty()
                    4 -> hasWordDetails(w)
                    else -> true
                })
        }
    }

    private fun key(w: Word) = "${questionDirection}_${w.id}"
    private fun question(w: Word) = if (questionDirection == 0) w.en else w.primaryRu
    private fun answer(w: Word) = if (questionDirection == 0) w.primaryRu else w.en
    private fun displayPronunciation(w: Word) = when (pronunciationMode) {
        "ipa_us" -> w.ipaUs.ifBlank { w.pron }
        "ipa_uk" -> w.ipaUk.ifBlank { w.pron }
        else -> w.pron
    }
    private fun priorityKey() = if (learningMode) "priority_completed_learn" else "priority_completed_test"
    private fun priorityCompleted(): Set<String> = prefs.getStringSet(priorityKey(), emptySet()).orEmpty()
    private fun questionLocale() = if (questionDirection == 0) Locale.US else Locale("ru", "RU")
    private fun answerLocale() = if (questionDirection == 0) Locale("ru", "RU") else Locale.US
    private fun updateWordImage(word: Word?, reveal: Boolean) {
        if (!::wordImage.isInitialized) return
        wordImage.setImageDrawable(null)
        wordImage.visibility = View.GONE
        if (word == null || !reveal || !prefs.getBoolean("show_illustrations", true)) return
        val entry = visualCatalog.entryFor(word.id) ?: return
        val bitmap = visualCatalog.loadImage(word.id) ?: return
        wordImage.setImageBitmap(bitmap)
        wordImage.contentDescription = entry.altEn
        wordImage.visibility = View.VISIBLE
    }

    private fun next(restoringAnswer: Boolean = false, savedDirection: Int? = null) {
        if (!running) return
        vosk.stop()
        writtenAnswer.clearFocus()
        writtenAnswer.text.clear()
        clearAnswerTimer()
        pauseLearningAudio()
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
            if (trackPriority) {
                val completed = priorityCompleted() +
                    originalBlockIds.filterNot { it in skippedInBlock }.map(Int::toString)
                prefs.edit().putStringSet(priorityKey(), completed).apply()
            }
            running = false; current = null; answering = false
            updateWordImage(null, false)
            pendingBlockErrors = emptyMap()
            val plan = learningPlan
            if (learningMode && plan != null) {
                plan.rememberPosition(0)
                finishedLessonSelection = FinishedLessonSelection(plan.lessons.size, plan.current)
                progress.progress = 100
                updateButtons()
                pageScroll.scrollTo(0, 0)
                persistActiveSession()
                return
            }
            prompt.text = if (remediationRound > 0) {
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
        updateWordImage(w, learningMode || restoringAnswer)
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
        translationText.visibility = if (learningMode) View.VISIBLE else View.GONE
        if (learningMode) {
            prompt.text = w.en
            prompt.textSize = when {
                w.en.length > 20 -> 34f
                w.en.length > 13 -> 42f
                else -> 52f
            }
            translationText.text = w.primaryRu
            pronunciation.text = displayPronunciation(w)
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
        pronunciation.text = if (questionDirection == 0) displayPronunciation(w) else "Произношение появится после ответа"
        status.text = if (ttsReady) "Слушай слово..." else "Озвучивание недоступно; можно отвечать кнопками"
        updateExamplesControls(w)
        updateFamilyControl(w)
        if (restoringAnswer) {
            answering = true
            updateFamilyControl(w)
            pronunciation.text = "${w.en}  •  ${displayPronunciation(w)}"
            // Always restore the answer visibly, even when the offline TTS voice is missing.
            translationText.text = answer(w)
            translationText.visibility = View.VISIBLE
            status.text = gradeLabel(lastGradeOk, lastGradeSkipped) +
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
            if (page == Page.CHECK && !checkPaused && running && current?.id == w.id && !answering) {
                status.text = if (uiEnglish) "Answer by voice, text or buttons"
                    else "Ответьте голосом, текстом или кнопками"
            }
        }
    }
    private fun hasWordDetails(word: Word) =
        word.family.isNotEmpty() || word.grammar.isNotEmpty() ||
            familyIndex.containsKey(word.en.lowercase(Locale.ROOT))

    private fun updateFamilyControl(word: Word) {
        // In RU → EN mode, reveal the English family only after the answer.
        val visible = hasWordDetails(word) &&
            (questionDirection == 0 || answering)
        familyButton.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) {
            val size = word.family.size.takeIf { it > 0 }
                ?: familyIndex[word.en.lowercase(Locale.ROOT)]?.members?.size ?: 0
            familyButton.text = if (uiEnglish) "Word family ($size)  ›"
                else "Семья слова ($size)  ›"
        }
    }

    private fun openFamily() {
        val target = current ?: return
        if (!hasWordDetails(target)) return
        val originalFamily = familyIndex[target.en.lowercase(Locale.ROOT)]
        val family = if (target.family.isNotEmpty()) WordFamily(
            target.en, target.primaryRu, target.grammar["Глагольный статус"].orEmpty(),
            target.family.map { line ->
                val parts = line.split(" — ", limit = 2)
                FamilyMember(parts.first(), parts.getOrElse(1) { "" }, "", "")
            }, emptyList(),
        ) else originalFamily ?: WordFamily(target.en, target.primaryRu, "", emptyList(), emptyList())
        if (familyDialog?.isShowing == true) return
        familyExpanded = true
        if (running && !answering && !learningMode) assistedThisWord = true
        clearAnswerTimer()
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
        if (target.grammar.isNotEmpty()) {
            add(root, text(if (uiEnglish) "GRAMMAR" else "ГРАММАТИКА",
                12f, muted, true).apply { gravity = Gravity.START }, top = 20)
            target.grammar.forEach { (label, value) ->
                add(root, text("$label: $value", 15f, navy).apply { gravity = Gravity.START }, top = 4)
            }
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
            clearAnswerTimer()
            say(item.word, Locale.US) {
                if (running && answering && !familyExpanded) scheduleAdvance()
            }
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { leftMargin = dp(8) })
        add(parent, row, top = 8)
    }

    private fun scenePhrases(word: Word): List<PhraseExample> {
        val contextual = scenes.firstOrNull { it.id == activeSceneId }?.examples?.get(word.id).orEmpty()
        val original = examplesByWord[word.en.lowercase(Locale.ROOT)].orEmpty()
        val imported = (word.collocations + word.phrasalVerbs).map { line ->
            val parts = line.split(" — ", limit = 2)
            PhraseExample(parts.first(), parts.getOrElse(1) { "" })
        }
        return (word.examplesEn.map { PhraseExample(it, "") } + contextual + original + imported).distinctBy { it.en.lowercase(Locale.ROOT) }
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
        clearAnswerTimer()
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
                clearAnswerTimer()
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
        bundledSpeech.stop()
    }

    private fun resumeLearningWord() {
        if (running && learningMode && !learningPaused && !examplesExpanded &&
            !familyExpanded && menu.visibility != View.VISIBLE) narrateLearningWord()
    }

    private fun narrateLearningWord() {
        val word = current ?: return
        if (page != Page.LEARN || !running || !learningMode || learningPaused ||
            examplesExpanded || familyExpanded || menu.visibility == View.VISIBLE) return
        if (lessonSound && !automaticAudioCheckDone) {
            if (automaticAudioCheckRunning) return
            automaticAudioCheckRunning = true
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), acousticPermissionRequestCode)
            } else {
                runAutomaticAcousticCheck()
            }
            return
        }
        pauseLearningAudio()
        if (lessonSound && bundledSpeechReady) {
            val playbackId = learningSequenceId
            val wordId = word.id
            status.text = if (uiEnglish) "Speaking with bundled offline voices"
                else "Озвучивание встроенными офлайн-голосами"
            bundledSpeech.speakPair(word.en, word.ru) { success ->
                runOnUiThread {
                    if (running && learningMode && !learningPaused && current?.id == wordId &&
                        playbackId == learningSequenceId && !manualLearning) {
                        if (!success) status.text = if (uiEnglish) "Bundled TTS playback failed"
                            else "Ошибка воспроизведения встроенного синтезатора"
                        handler.postDelayed(learningAdvanceRunnable, learningPauseSeconds * 1000L)
                    }
                }
            }
            return
        }
        if (!lessonSound || !ttsReady || englishOfflineVoices.isEmpty() || russianOfflineVoices.isEmpty()) {
            status.text = if (uiEnglish)
                "Offline EN/RU speech unavailable; automatic cards continue. Install both offline voices."
            else "Офлайн-озвучивание EN/RU недоступно; автоматическое перелистывание продолжается. Установите оба голоса."
            if (!manualLearning) handler.postDelayed(learningAdvanceRunnable, learningPauseSeconds * 1000L)
            return
        }
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
            if (!stillCurrent() || manualLearning) return
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
            status.text = if (uiEnglish) "Usage example" else "Пример употребления"
            say(example.en, Locale.US) {
                if (stillCurrent()) {
                    if (example.ru.isBlank()) speakExampleAt(index + 1)
                    else say(example.ru, Locale("ru", "RU")) {
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

    private fun clearAnswerTimer() {
        answerCountdown?.cancel()
        answerCountdown = null
        answerTimerLabel.visibility = View.GONE
    }

    private fun scheduleAdvance() {
        clearAnswerTimer()
        if (page != Page.CHECK || checkPaused || !running || !answering || examplesExpanded ||
            familyExpanded || nextDelaySeconds == 0) return
        answerTimerLabel.text = if (uiEnglish) "$nextDelaySeconds s" else "$nextDelaySeconds с"
        answerTimerLabel.visibility = View.VISIBLE
        answerCountdown = object : android.os.CountDownTimer(nextDelaySeconds * 1000L, 250L) {
            override fun onTick(remaining: Long) {
                val seconds = (remaining + 999) / 1000
                answerTimerLabel.text = if (uiEnglish) "$seconds s" else "$seconds с"
            }

            override fun onFinish() {
                advanceToNext()
            }
        }.start()
    }

    private fun advanceToNext() {
        if (!running || !answering) return
        clearAnswerTimer()
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
        if (page != Page.CHECK || checkPaused || !running || method != 0 || answering) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 12)
            return
        }
        val word = current ?: return
        tts.stop()
        val spokenLanguage = if (questionDirection == 0) "ru" else "en"
        val responseMillis = voiceResponseSeconds * 1_000L
        vosk.listen(spokenLanguage, responseMillis, includeAlternatives = true,
            onStatus = { status.text = it }) { result ->
            if (page != Page.CHECK || checkPaused || !running || answering || current !== word) {
                return@listen
            }
            when (result) {
                is VoiceRecognitionResult.Transcript -> {
                    if (result.matchesFirstHypothesis { isCorrect(it, word) }) {
                        grade(true, result.text, methodSource = "asr")
                    } else {
                        val alternatives = result.otherHypotheses.joinToString(", ")
                        val heard = if (uiEnglish) "Heard: «${result.text}»"
                            else "Распознано: «${result.text}»"
                        val hint = if (uiEnglish) "Retry, enter an answer or grade it manually."
                            else "Повторите, введите ответ или оцените его кнопками."
                        status.text = if (alternatives.isEmpty()) "$heard\n$hint" else {
                            val label = if (uiEnglish) "Other hypotheses" else "Другие варианты"
                            "$heard\n$label · $alternatives\n$hint"
                        }
                    }
                }
                VoiceRecognitionResult.Silence -> {
                    grade(false, if (uiEnglish) "No speech heard" else "Тишина",
                        methodSource = "asr", skipped = true)
                }
                VoiceRecognitionResult.Unrecognized -> {
                    status.text = if (uiEnglish) "Audio detected, speech unclear. Retry or type."
                        else "Звук есть, речь не распознана. Повторите или введите ответ."
                }
                VoiceRecognitionResult.MicrophoneError -> {
                    status.text = if (uiEnglish) "Microphone unavailable. Retry or type."
                        else "Сбой микрофона. Попробуйте снова или введите ответ."
                }
                VoiceRecognitionResult.RecognitionError -> {
                    status.text = if (uiEnglish) "Local recognition unavailable. Type your answer."
                        else "Локальное распознавание недоступно. Введите ответ."
                }
            }
        }
    }

    private fun normalize(s: String): String = Normalizer.normalize(
        s.lowercase(Locale.ROOT).replace('ё', 'е'), Normalizer.Form.NFD
    ).replace(Regex("\\p{M}+"), "").replace(Regex("[^\\p{L}\\p{N} ]"), " ")
        .trim().replace(Regex("\\s+"), " ")
    private fun isCorrect(heard: String, w: Word): Boolean {
        val variants = if (questionDirection == 0) w.acceptedRu else w.acceptedEn
        return if (variants.isEmpty()) VoiceAnswerMatcher.matches(heard, answer(w), questionDirection)
            else VoiceAnswerMatcher.matches(heard, variants, questionDirection)
    }
    private fun gradeLabel(ok: Boolean, skipped: Boolean): String = when {
        skipped -> if (uiEnglish) "Skipped" else "Пропуск"
        ok -> "Окей!"
        else -> "Не окей."
    }

    private fun grade(
        ok: Boolean, detail: String, methodSource: String = "manual",
        skipped: Boolean = false
    ) {
        val w = current ?: return
        if (page != Page.CHECK || checkPaused || !running || answering) return
        answering = true
        updateWordImage(w, true)
        vosk.stop()
        // A hinted "know" is practice, not an independently verified recall.
        if (!skipped && (!ok || !assistedThisWord)) {
            prefs.edit().putInt(key(w), if (ok) 1 else 0).apply()
        }
        if (skipped) {
            skippedInBlock = skippedInBlock + w.id
        } else if (!learningMode) {
            if (ok && !assistedThisWord) skippedInBlock = skippedInBlock - w.id
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
        pronunciation.text = "${w.en}  •  ${displayPronunciation(w)}"
        // Correction is visual-first: Android TTS warnings must not hide the translation.
        translationText.text = answer(w)
        translationText.visibility = View.VISIBLE
        status.text = gradeLabel(ok, skipped) +
            "\n$detail\nПравильно: ${answer(w)}$forms" +
            if (assistedThisWord) {
                if (uiEnglish) "\nAssisted answer: a clean retry is required."
                else "\nОтвет с подсказкой: повторим без неё."
            } else if (pendingBlockErrors.isNotEmpty()) {
                if (uiEnglish) "\nUnresolved in this block: ${pendingBlockErrors.size}"
                else "\nОсталось исправить в блоке: ${pendingBlockErrors.size}"
            } else ""
        lastGradeOk = ok
        lastGradeSkipped = skipped
        lastGradeDetail = detail
        nextWordButton.visibility = View.VISIBLE
        updateFamilyControl(w)
        updateButtons(); refresh()
        persistActiveSession()
        say(answer(w), answerLocale()) {
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
        if (page != Page.CHECK) openExercise(Page.CHECK)
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
                words.any { it.id == candidate.wordId && it.testEnabled }
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
        val count = blockSizes.getOrElse(blockSpinner.selectedItemPosition) { dictionarySettings.defaultBlockSize }
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
        vosk.stop()
        examplesExpanded = false
        familyExpanded = false
        learningMode = false
        learningPaused = false
        lessonModeSpinner.setSelection(1)
        trackPriority = false
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
        skippedInBlock = emptySet()
        forcedReviewDirections = selected.associate { it.wordId to it.direction }
        index = 0
        running = true
        answering = false
        assistedThisWord = false
        lastGradeDetail = ""
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

    private fun serializeLearningPlan(): String? = learningPlan?.let { plan ->
        JSONObject().apply {
            put("lessons", JSONArray(plan.lessons.map { JSONArray(it) }))
            put("positions", JSONArray(plan.positions.toList()))
            put("current", plan.current)
        }.toString()
    }

    private fun deserializeLearningPlan(raw: String?, activeWords: List<Int>): LearningLessonPlan? {
        if (raw.isNullOrEmpty()) return null
        return runCatching {
            val json = JSONObject(raw)
            val all = json.getJSONArray("lessons")
            require(all.length() in 1..words.size)
            val validIds = words.map { it.id }.toHashSet()
            val lessons = List(all.length()) { lesson ->
                val ids = all.getJSONArray(lesson)
                require(ids.length() in 1..50)
                List(ids.length()) { ids.getInt(it).also { id -> require(id in validIds) } }
            }
            val visited = json.getJSONArray("positions")
            require(visited.length() == lessons.size)
            val plan = LearningLessonPlan(lessons, IntArray(visited.length()) { visited.getInt(it) },
                json.getInt("current"))
            require(plan.words == activeWords)
            plan
        }.getOrNull()
    }

    /** Keep the active lesson independent of Activity recreation and process lifetime. */
    private fun persistActiveSession(commitNow: Boolean = false) {
        if (loadedMode == null) return
        val word = current
        val key = if (learningMode) "snapshot_learn" else "snapshot_check"
        val editor = sessionPrefs.edit()
        val completion = finishedLessonSelection.takeIf { learningMode && learningPlan != null }
        val snapshotIndex = if (completion == null) index else reviewIds.lastIndex
        val active = running && word != null && reviewIds.getOrNull(index) == word.id
        if ((!active && completion == null) || snapshotIndex !in reviewIds.indices) {
            editor.remove(key)
        } else {
            if (learningMode && completion == null) learningPlan?.rememberPosition(index)
            val snapshot = JSONObject().apply {
                put("schema", 1)
                if (learningMode) put("learningPlan", serializeLearningPlan())
                if (completion != null) put("finishedSelection", completion.index)
                put("ids", JSONArray(reviewIds))
                put("index", snapshotIndex)
                put("direction", direction)
                put("method", method)
                put("selection", selectionMode)
                put("trackPriority", trackPriority)
                put("questionDirection", questionDirection)
                put("answered", answering)
                put("lastGradeOk", lastGradeOk)
                put("lastGradeSkipped", lastGradeSkipped)
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
                put("skippedIds", JSONArray(skippedInBlock.toList()))
                put("forcedDirections", JSONArray(reviewIds.map {
                    forcedReviewDirections[it] ?: -1
                }))
            }
            editor.putString(key, snapshot.toString())
            val legacy = sessionPrefs.getString("snapshot", null)
            if (legacy != null &&
                runCatching { JSONObject(legacy).getBoolean("learningMode") == learningMode }.getOrDefault(false)) {
                editor.remove("snapshot")
            }
        }
        if (commitNow) editor.commit() else editor.apply()
    }

    private fun readPersistedSession(learn: Boolean): Bundle? {
        val key = if (learn) "snapshot_learn" else "snapshot_check"
        val raw = sessionPrefs.getString(key, null)
            ?: sessionPrefs.getString("snapshot", null)?.takeIf {
                runCatching { JSONObject(it).getBoolean("learningMode") == learn }.getOrDefault(false)
            } ?: return null
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
                putString("learning_plan", snapshot.optString("learningPlan"))
                if (learn && snapshot.has("finishedSelection")) {
                    putInt("finished_selection", snapshot.getInt("finishedSelection"))
                }
                putInt("lesson_index", idx)
                putInt("lesson_direction", dir)
                putInt("lesson_method", mode)
                putInt("lesson_selection", selection)
                putBoolean("lesson_track_priority", snapshot.optBoolean("trackPriority"))
                putInt("lesson_question_direction", questionDir)
                putBoolean("lesson_answered", snapshot.optBoolean("answered"))
                putBoolean("lesson_last_grade_ok", snapshot.optBoolean("lastGradeOk"))
                putBoolean("lesson_last_grade_skipped", snapshot.optBoolean("lastGradeSkipped"))
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
                val skipped = snapshot.optJSONArray("skippedIds")
                putIntArray("lesson_skipped_ids", IntArray(skipped?.length() ?: 0) { skipped!!.getInt(it) })
            }
        } catch (_: Exception) {
            sessionPrefs.edit().remove(key).apply()
            null
        }
    }

    override fun onPause() {
        resumeLearningAfterForeground = page == Page.LEARN && running && learningMode && !learningPaused
        resumeAnswerTimerAfterForeground = page == Page.CHECK && !checkPaused && running && !learningMode && answering
        clearAnswerTimer()
        pauseLearningAudio()
        if (::tts.isInitialized) tts.stop()
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
        outState.putString("screen", page.name)
        outState.putString("settings_return", returnFromSettings.name)
        if (running && current != null) {
            outState.putIntArray("lesson_ids", reviewIds.toIntArray())
            if (learningMode) outState.putString("learning_plan", serializeLearningPlan())
            outState.putInt("lesson_index", index)
            outState.putInt("lesson_direction", direction)
            outState.putInt("lesson_method", method)
            outState.putInt("lesson_selection", selectionMode)
            outState.putBoolean("lesson_track_priority", trackPriority)
            outState.putInt("lesson_question_direction", questionDirection)
            outState.putBoolean("lesson_answered", answering)
            outState.putBoolean("lesson_examples_expanded", examplesExpanded)
            outState.putBoolean("lesson_family_expanded", familyExpanded)
            outState.putBoolean("lesson_last_grade_ok", lastGradeOk)
            outState.putBoolean("lesson_last_grade_skipped", lastGradeSkipped)
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
            outState.putIntArray("lesson_skipped_ids", skippedInBlock.toIntArray())
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
        if (method == 0 && !packagedOfflineVoice()) method = 1
        selectionMode = state.getInt("lesson_selection", 0)
        trackPriority = state.getBoolean("lesson_track_priority", false)
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
        skippedInBlock = state.getIntArray("lesson_skipped_ids")?.filter { it in originalBlockIds }
            ?.toSet().orEmpty()
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
            blockSizes.indexOfFirst { it >= originalBlockIds.size }
                .coerceAtLeast(0)
        )
        if (learningMode) {
            val blockSize = blockSizes.getOrElse(blockSpinner.selectedItemPosition) {
                dictionarySettings.defaultBlockSize
            }
            learningPlan = deserializeLearningPlan(state.getString("learning_plan"), ids)
                ?: LearningLessonPlan.restoreLegacy(
                    ids, index, exerciseCandidates().map { it.id }, blockSize
                )
            val plan = learningPlan
            if (plan != null && state.containsKey("finished_selection")) {
                finishedLessonSelection = FinishedLessonSelection(
                    plan.lessons.size, plan.current, state.getInt("finished_selection")
                )
                progress.progress = 100
                updateWordImage(null, false)
                updateButtons()
                return
            }
            plan?.rememberPosition(index)
        }
        running = true
        answering = false
        lastGradeOk = state.getBoolean("lesson_last_grade_ok", false)
        lastGradeSkipped = state.getBoolean("lesson_last_grade_skipped", false)
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
        clearAnswerTimer()
        handler.removeCallbacksAndMessages(null)
        examplesDialog?.dismiss()
        familyDialog?.dismiss()
        pauseLearningAudio()
        bundledSpeech.close()
        vosk.release(); tts.stop(); tts.shutdown()
        super.onDestroy()
    }
}
