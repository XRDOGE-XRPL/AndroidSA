package com.xrdoge.androidsa

object NativeBridge {
    private val nativeLayerLoaded: Boolean = runCatching {
        System.loadLibrary("androidsa")
        true
    }.getOrDefault(false)

    fun isNativeLayerAvailable(): Boolean = nativeLayerLoaded

    fun runtimeStatus(): String {
        return if (nativeLayerLoaded) {
            "Native runtime available"
        } else {
            "Native runtime scaffolded; JNI binding pending"
        }
    }
}
