package com.example.voicechanger

object NativeAudio {
    init {
        System.loadLibrary("native_audio")
    }

    external fun nativeInit(): Boolean
    external fun nativeStart(): Boolean
    external fun nativeStop()
    external fun nativeVersion(): String
}
