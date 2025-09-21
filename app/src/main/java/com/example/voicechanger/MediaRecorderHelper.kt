package com.example.voicechanger

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.core.content.ContextCompat
import android.util.Log

/**
 * MediaRecorderHelper: demonstrates safe MediaRecorder usage from a background thread with a Looper.
 * - Checks RECORD_AUDIO permission
 * - Uses HandlerThread to create MediaRecorder on a thread with a Looper
 * - Registers OnInfoListener and OnErrorListener
 */
class MediaRecorderHelper(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null

    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    fun startRecording(outputPath: String, onStarted: (() -> Unit)? = null, onError: ((String) -> Unit)? = null) {
        if (!hasRecordPermission()) {
            onError?.invoke("Missing RECORD_AUDIO permission")
            return
        }

        handlerThread = HandlerThread("MediaRecorderThread").also { it.start() }
        handler = Handler(handlerThread!!.looper)

        handler?.post {
            try {
                recorder = MediaRecorder().apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                    setOutputFile(outputPath)
                    setOnInfoListener { mr, what, extra ->
                        Log.i("MediaRecorderHelper", "onInfo: $what / $extra")
                    }
                    setOnErrorListener { mr, what, extra ->
                        Log.e("MediaRecorderHelper", "onError: $what / $extra")
                        onError?.invoke("MediaRecorder error: $what")
                    }
                    prepare()
                    start()
                }
                onStarted?.invoke()
            } catch (se: SecurityException) {
                Log.e("MediaRecorderHelper", "SecurityException: ${se.message}")
                onError?.invoke("SecurityException: ${se.message}")
                stopAndReleaseInternal()
            } catch (t: Throwable) {
                Log.e("MediaRecorderHelper", "Failed to start MediaRecorder: ${t.message}")
                onError?.invoke("Failed to start: ${t.message}")
                stopAndReleaseInternal()
            }
        }
    }

    fun stopRecording(onStopped: (() -> Unit)? = null) {
        handler?.post {
            try {
                recorder?.stop()
            } catch (t: Throwable) {
                Log.w("MediaRecorderHelper", "stop() failed: ${t.message}")
            }
            stopAndReleaseInternal()
            onStopped?.invoke()
        }
    }

    private fun stopAndReleaseInternal() {
        try { recorder?.reset() } catch (_: Throwable) {}
        try { recorder?.release() } catch (_: Throwable) {}
        recorder = null
        try { handlerThread?.quitSafely() } catch (_: Throwable) {}
        handlerThread = null
        handler = null
    }
}
