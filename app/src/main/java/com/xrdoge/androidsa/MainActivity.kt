package com.xrdoge.androidsa

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.xrdoge.androidsa.ui.AppRuntimeState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val runtimeState = AppRuntimeState(
            bridgeReady = NativeBridge.isNativeLayerAvailable(),
            networkStatus = NativeBridge.runtimeStatus()
        )

        setContentView(
            TextView(this).apply {
                text = getString(
                    R.string.app_welcome_format,
                    if (runtimeState.bridgeReady) getString(R.string.bridge_ready) else getString(R.string.bridge_stub),
                    runtimeState.networkStatus
                )
                textSize = 18f
                setPadding(48, 48, 48, 48)
            }
        )
    }
}
