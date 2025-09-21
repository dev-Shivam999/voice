package com.example.voicechanger

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlin.concurrent.thread
import android.util.Log
import kotlin.math.*

class AudioEngine(private val ctx: Context? = null, private val preferredSource: Int? = null) {
    @Volatile
    private var running = false
    private var ioThread: Thread? = null

    fun start(effectIndex: Int) {
        if (running) return
        running = true

    // prefer device native sample rate when available
    val audioManager = ctx?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    val nativeSampleRate = audioManager?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
    val sampleRate = nativeSampleRate ?: 44100
        val channelInConfig = AudioFormat.CHANNEL_IN_MONO
        val channelOutConfig = AudioFormat.CHANNEL_OUT_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        var minRec = AudioRecord.getMinBufferSize(sampleRate, channelInConfig, audioFormat)
        var minPlay = AudioTrack.getMinBufferSize(sampleRate, channelOutConfig, audioFormat)

        if (minRec <= 0) minRec = 2048
        if (minPlay <= 0) minPlay = 2048

        val recBuf = max(minRec, 2048)
        val playBuf = max(minPlay, 2048)

        val audioFormatIn = android.media.AudioFormat.Builder()
            .setEncoding(audioFormat)
            .setSampleRate(sampleRate)
            .setChannelMask(channelInConfig)
            .build()

        // choose audio source: honor explicit preferredSource, else MIC then VOICE_COMMUNICATION
        val sourcesToTry = listOfNotNull(preferredSource, MediaRecorder.AudioSource.MIC, MediaRecorder.AudioSource.VOICE_COMMUNICATION)
        var recorder: AudioRecord? = null
        for (src in sourcesToTry) {
            try {
                val r = AudioRecord.Builder()
                    .setAudioSource(src)
                    .setAudioFormat(audioFormatIn)
                    .setBufferSizeInBytes(recBuf)
                    .build()
                if (r.state == AudioRecord.STATE_INITIALIZED) {
                    recorder = r
                    Log.i("AudioEngine", "Initialized AudioRecord with source=$src")
                    break
                } else {
                    try { r.release() } catch (_: Throwable) {}
                }
            } catch (t: Throwable) {
                Log.w("AudioEngine", "AudioRecord init failed for source=$src: ${t.message}")
            }
        }

        if (recorder == null) {
            Log.e("AudioEngine", "No usable AudioRecord source found. Aborting start().")
            running = false
            return
        }

        val audioFormatOut = android.media.AudioFormat.Builder()
            .setEncoding(audioFormat)
            .setSampleRate(sampleRate)
            .setChannelMask(channelOutConfig)
            .build()

        val audioAttrs = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
            .setLegacyStreamType(AudioManager.STREAM_VOICE_CALL)
            .build()

        val track = AudioTrack.Builder()
            .setAudioAttributes(audioAttrs)
            .setAudioFormat(audioFormatOut)
            .setBufferSizeInBytes(playBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            Log.e("AudioEngine", "AudioTrack failed to initialize (state=${track.state}). Aborting start().")
            try { track.release() } catch (_: Throwable) {}
            try { recorder.release() } catch (_: Throwable) {}
            running = false
            return
        }

        try {
            recorder.startRecording()
            track.play()
        } catch (t: Throwable) {
            Log.e("AudioEngine", "Failed to start audio I/O: ${t.message}")
            try { recorder.release() } catch (_: Throwable) {}
            try { track.release() } catch (_: Throwable) {}
            running = false
            return
        }

        ioThread = thread(start = true) {
            val buffer = ShortArray(1024)

            while (running) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val processed = applyEffect(buffer, read, effectIndex, sampleRate)
                    track.write(processed, 0, read)
                }
            }

            try { recorder.stop() } catch (_: Throwable) {}
            try { recorder.release() } catch (_: Throwable) {}
            track.stop()
            track.release()
        }
    }

    fun stop() {
        running = false
        ioThread?.join()
        ioThread = null
    }

    private fun applyEffect(input: ShortArray, length: Int, effectIndex: Int, sampleRate: Int): ShortArray {
        val out = ShortArray(length)

        when (effectIndex) {
            0 -> { // None
                System.arraycopy(input, 0, out, 0, length)
            }
            1 -> { // Child - pitch shift up
                pitchShift(input, out, length, 1.6)
            }
            2 -> { // Cowboy - slight pitch down + echo
                pitchShift(input, out, length, 0.8)
                // simple echo
                for (i in 0 until length) {
                    val echoIdx = i - 400
                    if (echoIdx >= 0) {
                        val sInt = out[i].toInt() + (out[echoIdx].toInt() / 2)
                        out[i] = sInt.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    }
                }
            }
            3 -> { // Robot - ring modulation + bitcrusher
                for (i in 0 until length) {
                    val mod = (sin(2.0 * Math.PI * 30.0 * i / sampleRate) * Short.MAX_VALUE).toInt()
                    val vDouble = input[i].toDouble() * (mod / Short.MAX_VALUE.toDouble())
                    // bitcrush: quantize to 256-step levels
                    val quantized = ((vDouble / 256.0).toInt() * 256)
                    val qInt = quantized.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    out[i] = qInt.toShort()
                }
            }
            4 -> { // Female - mild pitch up + formant shift (approx)
                pitchShift(input, out, length, 1.2)
            }
            5 -> { // OldMale - pitch down + lowpass
                pitchShift(input, out, length, 0.7)
                // simple one-pole lowpass
                var prev = 0.0
                val a = 0.2
                for (i in 0 until length) {
                    val cur = a * out[i] + (1 - a) * prev
                    out[i] = cur.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    prev = cur
                }
            }
        }

        return out
    }

    private fun pitchShift(input: ShortArray, output: ShortArray, length: Int, pitch: Double) {
        // crude nearest-neighbor resampling for pitch shift (fast but low quality)
        for (i in 0 until length) {
            val srcIdx = (i / pitch).toInt()
            val vInt = if (srcIdx in 0 until length) input[srcIdx].toInt() else 0
            output[i] = vInt.toShort()
        }
    }
}
