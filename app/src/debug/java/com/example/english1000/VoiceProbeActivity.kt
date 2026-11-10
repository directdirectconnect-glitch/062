package com.example.english1000

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Base64
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView

/**
 * Debug-only ADB acoustic fixture for the isolated S20 test package.
 * Never included in production release APKs.
 *
 * Host waits until VOICE_PROBE_READY, plays an offline WAV over a physical
 * speaker, then reads VOICE_PROBE_PASS/FAIL from Android UI automation.
 * The local transcript is deliberately not printed into public CI logs.
 */
class VoiceProbeActivity : Activity() {
    private var engine: OfflineVoskEngine? = null
    private lateinit var status: TextView
    private var started = false
    private var acceptedExpected: String = ""

    private fun suppliedWord(): String = try {
        val encoded = intent.getStringExtra("expected_b64") ?: ""
        String(Base64.decode(encoded, Base64.NO_WRAP), Charsets.UTF_8)
    } catch (_: Exception) { "" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val language = intent.getStringExtra("lang") ?: ""
        val expected = suppliedWord()
        acceptedExpected = expected
        status = TextView(this).apply {
            text = "VOICE_PROBE_LOADING"
            textSize = 26f
            gravity = Gravity.CENTER
            contentDescription = "VOICE_PROBE_LOADING"
            setPadding(24, 24, 24, 24)
        }
        setContentView(status, ViewGroup.LayoutParams(-1, -1))
        if (language !in setOf("en", "ru") ||
            expected.isBlank() || expected.length > 80) {
            setState("VOICE_PROBE_ERROR")
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 143)
        } else {
            startProbe()
        }
    }

    private fun setState(value: String) {
        status.text = value
        status.contentDescription = value
    }

    private fun startProbe() {
        if (started) return
        started = true
        val language = intent.getStringExtra("lang") ?: return
        val expected = acceptedExpected
        val recognizer = OfflineVoskEngine(this)
        engine = recognizer
        recognizer.listen(
            language,
            onStatus = { message ->
                if (!isFinishing && !isDestroyed) {
                    setState(if (message.contains("слушаю")) {
                        "VOICE_PROBE_READY"
                    } else {
                        "VOICE_PROBE_LOADING"
                    })
                }
            },
            onTranscript = { spoken ->
                if (!isFinishing && !isDestroyed) {
                    setState(if (VoiceAnswerMatcher.matches(spoken, expected,
                            if (language == "ru") 0 else 1)) {
                        "VOICE_PROBE_PASS"
                    } else {
                        "VOICE_PROBE_FAIL"
                    })
                }
            },
            onError = { _ ->
                if (!isFinishing && !isDestroyed) setState("VOICE_PROBE_ERROR")
            }
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, results: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 143) {
            if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startProbe()
            else setState("VOICE_PROBE_ERROR")
        }
    }

    override fun onDestroy() {
        engine?.stop()
        engine = null
        super.onDestroy()
    }
}
