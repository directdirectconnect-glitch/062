package com.example.english1000

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Process
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Explicit, local speaker-to-microphone acoustic diagnostic.
 * No audio is saved, transmitted, or used for speech recognition.
 * Must be called only after RECORD_AUDIO runtime permission is granted.
 */
internal object AcousticLoopbackCheck {
    data class Result(val passed: Boolean, val code: String, val detail: String)
    private const val RATE = 16000
    private const val HZ = 997.0
    private const val SAMPLES = 6400 // 400 ms
    private const val RECORD_SAMPLES = 14400 // 900 ms
    @Volatile private var active = false

    fun run(context: Context, callback: (Result) -> Unit) {
        if (active) {
            callback(Result(false, "BUSY", "Проверка уже выполняется"))
            return
        }
        active = true
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            var recorder: AudioRecord? = null
            var player: AudioTrack? = null
            try {
                val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                if (manager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
                    callback(Result(false, "MUTED", "Громкость мультимедиа равна нулю"))
                    return@Thread
                }
                if (manager.isBluetoothA2dpOn || manager.isBluetoothScoOn ||
                    manager.isWiredHeadsetOn) {
                    callback(Result(false, "EXTERNAL_AUDIO", "Отключите наушники и Bluetooth-аудио"))
                    return@Thread
                }
                val minBuffer = AudioRecord.getMinBufferSize(RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                if (minBuffer <= 0) {
                    callback(Result(false, "MIC_UNAVAILABLE", "Микрофон не поддерживает тестовый формат"))
                    return@Thread
                }
                recorder = AudioRecord(MediaRecorder.AudioSource.MIC, RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBuffer, RATE * 2))
                if (recorder!!.state != AudioRecord.STATE_INITIALIZED) {
                    callback(Result(false, "MIC_INIT_FAILED", "Не удалось открыть микрофон"))
                    return@Thread
                }
                val wave = ShortArray(SAMPLES) { i ->
                    val ramp = minOf(1.0, i / 400.0, (SAMPLES - 1 - i) / 400.0)
                    (sin(2 * PI * HZ * i / RATE) * 7000 * ramp.coerceAtLeast(0.0)).toInt().toShort()
                }
                player = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(SAMPLES * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                if (player!!.state != AudioTrack.STATE_INITIALIZED ||
                    player!!.write(wave, 0, wave.size) != wave.size) {
                    callback(Result(false, "SPEAKER_INIT_FAILED", "Не удалось подготовить динамик"))
                    return@Thread
                }
                val captured = ShortArray(RECORD_SAMPLES)
                recorder!!.startRecording()
                player!!.play()
                var offset = 0
                while (offset < captured.size) {
                    val n = recorder!!.read(captured, offset, captured.size - offset)
                    if (n <= 0) break
                    offset += n
                }
                if (offset < RATE / 2) {
                    callback(Result(false, "MIC_READ_FAILED", "Микрофон не записал тестовый сигнал"))
                    return@Thread
                }
                // Evaluate short windows: avoid phase cancellation and account for latency.
                val window = 1600
                var peakTone = 0.0
                var peakRms = 0.0
                for (start in 0..(offset - window) step 800) {
                    var re = 0.0
                    var im = 0.0
                    var power = 0.0
                    for (i in 0 until window) {
                        val sample = captured[start + i].toDouble()
                        val angle = 2 * PI * HZ * i / RATE
                        re += sample * cos(angle)
                        im += sample * sin(angle)
                        power += sample * sample
                    }
                    val tone = 2 * sqrt(re * re + im * im) / window
                    peakTone = maxOf(peakTone, tone)
                    peakRms = maxOf(peakRms, sqrt(power / window))
                }
                val ratio = if (peakRms > 0) peakTone / peakRms else 0.0
                if (peakTone > 120.0 && ratio > 0.35) {
                    callback(Result(true, "ACOUSTIC_PASS", "Микрофон обнаружил сигнал динамика"))
                } else {
                    callback(Result(false, "SIGNAL_NOT_DETECTED",
                        "Сигнал не обнаружен: проверьте громкость, шум и доступ к микрофону"))
                }
            } catch (e: SecurityException) {
                callback(Result(false, "PERMISSION_DENIED", "Нет разрешения на микрофон"))
            } catch (e: Exception) {
                callback(Result(false, "AUDIO_ERROR", e.message ?: "Ошибка аудиосистемы"))
            } finally {
                try { player?.pause() } catch (_: Exception) {}
                try { player?.flush() } catch (_: Exception) {}
                try { player?.release() } catch (_: Exception) {}
                try { recorder?.stop() } catch (_: Exception) {}
                try { recorder?.release() } catch (_: Exception) {}
                active = false
            }
        }, "EnglishWords-AcousticCheck").start()
    }
}
