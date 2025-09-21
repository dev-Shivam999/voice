package com.example.voicechanger

import android.content.Context
import android.media.*
import android.util.Log

class AudioEngine(private val context: Context) {
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isRunning = false
    private var thread: Thread? = null

    fun start(effect: Int) {
        val sampleRate = 44100
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
            AudioTrack.MODE_STREAM
        )

        isRunning = true
        audioRecord?.startRecording()
        audioTrack?.play()

        thread = Thread {
            val buffer = ByteArray(bufferSize)
            while (isRunning) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    applyEffect(buffer, effect)
                    audioTrack?.write(buffer, 0, read)
                }
            }
        }
        thread?.start()
    }

    fun stop() {
        isRunning = false
        thread?.join()
        audioRecord?.stop()
        audioTrack?.stop()
        audioRecord?.release()
        audioTrack?.release()
        audioRecord = null
        audioTrack = null
    }

    private fun applyEffect(buffer: ByteArray, effect: Int) {
        // Simple pitch shift or volume change can be applied here
        // For now, this is a placeholder
        if (effect == 1) {
            for (i in buffer.indices) {
                buffer[i] = (buffer[i] * 1.5).toInt().coerceIn(Byte.MIN_VALUE.toInt(), Byte.MAX_VALUE.toInt()).toByte()
            }
        }
    }
}