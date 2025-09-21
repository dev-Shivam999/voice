package com.example.voicechanger

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val effects = listOf("None", "Child", "Cowboy", "Robot", "Female", "OldMale")

    private var engine: AudioEngine? = null

    private lateinit var effectsSpinner: Spinner
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var statusText: TextView
    private lateinit var nativeInitButton: Button
    private lateinit var nativeStartButton: Button

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) setup()
        else statusText.text = "Microphone permission required"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        effectsSpinner = findViewById(R.id.effectsSpinner)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        statusText = findViewById(R.id.status)
        nativeInitButton = findViewById(R.id.nativeInitButton)
        nativeStartButton = findViewById(R.id.nativeStartButton)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, effects)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        effectsSpinner.adapter = adapter

        startButton.setOnClickListener {
            val effect = effectsSpinner.selectedItemPosition
            engine = AudioEngine(this)
            engine?.start(effect)
            statusText.text = "Running: ${effects[effect]}"
            startButton.isEnabled = false
            stopButton.isEnabled = true
        }

        stopButton.setOnClickListener {
            engine?.stop()
            engine = null
            statusText.text = "Stopped"
            startButton.isEnabled = true
            stopButton.isEnabled = false
        }

        nativeInitButton.setOnClickListener {
            val ok = NativeAudio.nativeInit()
            statusText.text = if (ok) "Native init OK" else "Native init failed"
        }

        var nativeRunning = false
        nativeStartButton.setOnClickListener {
            if (!nativeRunning) {
                val ok = NativeAudio.nativeStart()
                nativeRunning = ok
                nativeStartButton.text = if (ok) "Native Stop" else "Native Start/Stop"
            } else {
                NativeAudio.nativeStop()
                nativeRunning = false
                nativeStartButton.text = "Native Start/Stop"
            }
        }

        // Overlay control
        val btnOverlay = Button(this).apply { text = "Show Widget" }
        (findViewById(android.R.id.content) as ViewGroup).addView(btnOverlay)
        btnOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
            } else {
                startService(Intent(this, FloatingWidgetService::class.java))
            }
        }

        checkPermissionAndSetup()
    }

    private fun checkPermissionAndSetup() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else setup()
    }

    private fun setup() {
        statusText.text = "Ready"
    }
}