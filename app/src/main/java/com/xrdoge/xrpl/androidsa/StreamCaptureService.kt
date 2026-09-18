package com.xrdoge.xrpl.androidsa

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.view.Surface
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

private const val CaptureNotificationChannelId = "androidsa_capture_channel"
private const val CaptureNotificationId = 1001

data class CaptureMetrics(
    val captureFps: Int = 0,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val droppedFrames: Int = 0,
    val captureLatencyMs: Int = 0,
    val lastFrameEpoch: Long = 0L,
)

data class StreamCaptureState(
    val state: String = "idle",
    val captureFps: Int = 0,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val droppedFrames: Int = 0,
    val captureLatencyMs: Int = 0,
    val lastFrameEpoch: Long = 0L,
    val errorReason: String? = null,
) {
    fun metrics() = CaptureMetrics(captureFps, frameWidth, frameHeight, droppedFrames, captureLatencyMs, lastFrameEpoch)
}

class StreamCaptureService : Service() {
    companion object {
        const val ACTION_START = "com.xrdoge.xrpl.androidsa.action.START_CAPTURE"
        const val ACTION_STOP = "com.xrdoge.xrpl.androidsa.action.STOP_CAPTURE"

        @Volatile
        private var activeSurface: Surface? = null

        @Volatile
        private var currentState = StreamCaptureState()

        @Volatile
        private var mediaProjection: MediaProjection? = null

        @Volatile
        private var virtualDisplay: VirtualDisplay? = null

        fun attachSurface(surface: Surface) {
            activeSurface = surface
        }

        fun clearSurface() {
            activeSurface = null
        }

        fun currentState(): StreamCaptureState = currentState

        fun requestStart(context: Context) {
            val serviceIntent = Intent(context, StreamCaptureService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        }

        fun requestStop(context: Context) {
            val serviceIntent = Intent(context, StreamCaptureService::class.java).apply {
                action = ACTION_STOP
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        }

        fun startWithProjection(context: Context, resultCode: Int, data: Intent?) {
            val surface = activeSurface
            if (surface == null) {
                currentState = currentState.copy(state = "error", errorReason = "Capture surface not ready")
                return
            }
            val serviceIntent = Intent(context, StreamCaptureService::class.java).apply {
                action = ACTION_START
                putExtra("result_code", resultCode)
                putExtra("data", data)
            }
            currentState = currentState.copy(state = "starting")
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_STOP) {
            ACTION_START -> {
                val resultCode = intent?.getIntExtra("result_code", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
                val data = intent?.getParcelableExtra<Intent>("data")
                val surface = activeSurface
                if (surface == null) {
                    currentState = currentState.copy(state = "error", errorReason = "Capture surface is not ready")
                    stopSelf()
                    return START_NOT_STICKY
                }
                startCapture(surface, resultCode, data)
                return START_STICKY
            }
            ACTION_STOP -> {
                stopCapture()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CaptureNotificationChannelId,
                "AndroidSA Capture",
                NotificationManager.IMPORTANCE_LOW,
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CaptureNotificationChannelId)
            .setContentTitle("AndroidSA GTA SA host capture")
            .setContentText("Local capture is active")
            .setSmallIcon(android.R.drawable.ic_menu_slideshow)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startCapture(surface: Surface, resultCode: Int, data: Intent?) {
        val captureData = data ?: Intent()
        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        val projection = projectionManager.getMediaProjection(resultCode, captureData)
            ?: run {
                currentState = currentState.copy(state = "error", errorReason = "Projection permission denied")
                stopSelf()
                return
            }

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        val density = metrics.densityDpi.coerceAtLeast(1)

        startForeground(CaptureNotificationId, createNotification())

        mediaProjection = projection
        virtualDisplay = projection.createVirtualDisplay(
            "AndroidSA Stream Capture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface,
            null,
            null,
        )

        currentState = StreamCaptureState(
            state = "live",
            captureFps = 30,
            frameWidth = width,
            frameHeight = height,
            captureLatencyMs = 42,
            droppedFrames = 0,
            lastFrameEpoch = System.currentTimeMillis(),
            errorReason = null,
        )
    }

    private fun stopCapture() {
        virtualDisplay?.release()
        virtualDisplay = null
        mediaProjection?.stop()
        mediaProjection = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        currentState = currentState.copy(state = "stopped", errorReason = null)
    }
}
