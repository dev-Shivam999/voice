package com.example.voicechanger

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner

class FloatingWidgetService : Service() {
    private var windowManager: WindowManager? = null
    private var widgetView: View? = null
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        widgetView = inflater.inflate(R.layout.floating_widget, null)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.START or Gravity.CENTER_VERTICAL

        windowManager?.addView(widgetView, params)

        val spinner = widgetView!!.findViewById<Spinner>(R.id.spinnerEffects)
        val btn = widgetView!!.findViewById<Button>(R.id.btnToggle)
        val effects = listOf("None", "Child", "Cowboy", "Robot", "Female", "OldMale")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, effects)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        btn.setOnClickListener {
            if (!running) startVoice(spinner.selectedItemPosition)
            else stopVoice()
        }
    }

    private fun startVoice(effect: Int) {
        val intent = Intent(this, VoiceService::class.java)
        intent.action = VoiceService.ACTION_START
        intent.putExtra(VoiceService.EXTRA_EFFECT, effect)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        running = true
    }

    private fun stopVoice() {
        val intent = Intent(this, VoiceService::class.java)
        intent.action = VoiceService.ACTION_STOP
        startService(intent)
        running = false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (widgetView != null) windowManager?.removeView(widgetView)
    }
}
