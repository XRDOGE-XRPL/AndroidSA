package com.xrdoge.androidsa

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            TextView(this).apply {
                text = getString(R.string.app_welcome)
                textSize = 18f
                setPadding(48, 48, 48, 48)
            }
        )
    }
}
