VoiceChangerApp - Realtime Android Voice Changer Prototype

What this is

- Minimal prototype Android app demonstrating a realtime audio loop (microphone -> processing -> playback) with simple effects: Child, Cowboy, Robot, Female, OldMale.

How to build

- Open the `VoiceChangerApp` folder in Android Studio.
- Let Gradle sync and build.
- Run on a physical device (recommended) with microphone access.

Notes & Limitations

- This prototype processes audio in the app only; it cannot replace the system microphone for other apps (e.g., in-game voice chat or Instagram calls) on unmodified Android devices. System-wide mic replacement requires a virtual audio driver or platform-level integration and/or root.
- Audio quality of pitch-shifting here is intentionally simple (nearest-neighbor resampling) for clarity. Replace with phase-vocoder / WSOLA / SoundTouch for production quality.
- Low-latency performance depends on device hardware; test on target devices.

Next steps

- Improve pitch-shifting algorithm (use native C++ + Oboe or SoundTouch).
- Add lower-level permission handling and run-time audio focus management.

Overlay & Floating Widget

- To show the floating widget (side popup) tap the "Show Widget" button in the app. On Android 6+ you will be asked to grant "Display over other apps" (overlay) permission — follow the prompt and enable it for this app.
- Once granted, tapping the button again will start the floating widget. Use the widget to toggle the voice engine and select effects while another app (game, call, etc.) is in the foreground.

Important: system-wide limitations

- This app runs a background (foreground) service to keep processing while other apps run, and provides an overlay to control effects. However, it cannot inject processed microphone audio into another app's microphone input on standard Android devices.
- If you need whole-system voice replacement (so other apps see your modified voice), you need one of:
  - A virtual audio driver / loopback device at OS/kernel level (requires device/vendor work),
  - A custom ROM or rooted device and advanced audio routing changes, or
  - App-level integration where the target app allows a custom audio source or plugin.

If you want help with any of these options (SoundTouch JNI, Oboe NDK porting, or exploring virtual audio solutions), tell me which direction you prefer and I'll implement the next steps.

External microphone (USB/Bluetooth) notes

- USB microphones or USB audio interfaces: many Android devices support USB audio via USB OTG. Plugging a USB mic or audio interface will often make it available as the input source. In some devices you can select it at the system level; otherwise the OS may automatically prefer the external device.
- Bluetooth microphones/headsets: when connected and active, Android may route the mic audio from the Bluetooth device. Bluetooth SCO/headset profile may be used for call audio and has lower quality/latency.
- Selecting audio source in app: Android's `MediaRecorder.AudioSource` and `AudioRecord` let you choose common sources (e.g., `MIC`, `VOICE_COMMUNICATION`, `CAMCORDER`). The OS chooses the actual device; explicit device selection usually requires the `AudioManager` and newer APIs (AudioDeviceInfo) or platform support.
- Permissions: still need `RECORD_AUDIO` at runtime. USB host permissions (if using USB OTG) may also be necessary on some devices.

Practical tips

- Test the external microphone on your device: connect the mic, open the app, and verify the audio input using a simple recording app first.
- If the external mic isn't picked automatically, try `VOICE_COMMUNICATION` as the audio source in `AudioRecord`.
- For production low-latency routing with external hardware, consider implementing audio via the NDK (Oboe) and handling `AudioDeviceCallback` to select the preferred `AudioDeviceInfo`.

MediaRecorder vs AudioRecord

- `MediaRecorder` is a high-level API intended for simple audio (and video) recording to files. It's easy to use and manages codec/format for you. Use it when you only need to record to a file or stream and don't need sample-level processing while recording.
- `AudioRecord` (or the NDK `Oboe`) is for low-level access to raw PCM audio buffers. Use `AudioRecord` when you need realtime processing (like live voice changing), low latency, or to stream processed audio elsewhere.

MediaRecorder lifecycle & permission notes

- Always check `RECORD_AUDIO` permission before creating or starting `MediaRecorder`. A `SecurityException` can be thrown at `prepare()` or `start()` if permission isn't granted.
- Create `MediaRecorder` on a thread with a Looper (e.g., `HandlerThread`) if you register `OnInfoListener` or `OnErrorListener` so callbacks are delivered correctly.
- Use `reset()` to reuse a `MediaRecorder` instance; call `release()` when you are done to free resources.

See `app/src/main/java/com/example/voicechanger/MediaRecorderHelper.kt` for a small safe example.

Using The App Without An External Microphone

- **Default behavior:** The app now prefers the device's built-in microphone (`MIC`) and will fall back to `VOICE_COMMUNICATION` if needed. You do not need an external USB or Bluetooth mic for the app to work.
- **Permissions:** Grant `RECORD_AUDIO` at runtime when prompted. Also allow the overlay permission if you want the floating widget.
- **How to test with the internal mic:**
  - Connect the Android device to your development machine and run the app from Android Studio, or install the APK and open it on the device.
  - Open the app, grant microphone permission when asked.
  - Tap `Show Widget` and allow overlay permission if requested.
  - In the app select an effect from the spinner and tap `Start` to begin processing. Speak into the device's built-in mic and you should hear the processed audio played back through the speaker or headset.
- **Troubleshooting:**
  - If you hear no audio, check Logcat for `AudioEngine` tags. The engine logs which audio source was initialized and any initialization errors.
  - If the device mutes playback when recording (some devices do), try using wired headphones or change the audio attributes in `AudioEngine` to `USAGE_MEDIA` for playback routing.
  - For best results test on a physical device. Emulators often don't expose real microphone hardware or give different routing behavior.

If you want me to force a specific input device (for example to always try USB), I can add a small UI control and the code to prefer that `AudioDeviceInfo` when present.

Why your friend may still hear your original voice (and why you hear loud beep/feedback)

- Feedback / loud beep: if you run the engine and play processed audio back through the device speaker while the mic is active, the speaker output can be re-captured by the microphone and cause a loud echo/feedback loop. To avoid this during testing, use wired headphones or Bluetooth (headset may route mic differently).
- Calls and other apps still hear original voice: Android apps cannot normally replace the system microphone input seen by other apps. This app captures the mic in-app, processes and plays it back locally. It does not inject the processed audio into another app's microphone stream (e.g., a VoIP/call). For other apps to hear the changed audio you need: a virtual audio driver/loopback at OS level, platform/vendor support, or a rooted device with custom routing.

Practical testing tips

- To test changes without feedback: use wired headphones or a headset so processed audio isn't re-captured by the mic.
- To test whether the app is processing your mic at all: start the app, enable an effect, and listen on the device (or headphones) — you should hear the changed audio locally.
- To test with a friend over a call app: if you want other apps to hear the modified audio you will need one of the system-level solutions above. Alternatively, test by recording the processed audio to a file (use `MediaRecorderHelper`) and share the recording.

If you'd like, I can:

- Add a headphone-only detection hint (prompt to use headphones when starting) and automatically switch playback attributes for better routing.
- Add a recording button so you can save a sample file of the processed audio and send it to your friend.
- Start a plan for a deeper system-level approach (Oboe + SoundTouch + instructions for routing), but note that making other apps receive modified mic input requires OS-level support and often can't be done purely in an app on unmodified Android.
