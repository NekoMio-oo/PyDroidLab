package com.pydroidlab.app.runtime

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.annotation.Keep
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Native Android Hardware & System Bridge exposed to Python via `import android_app`.
 * Annotated with `@Keep` so R8/ProGuard preserves all methods invoked from Chaquopy.
 */
@Keep
class AndroidHardwareBridge(
    private val context: Context,
    private val onShowImageOnUi: (File) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val notificationIdCounter = AtomicInteger(1001)
    private var ttsEngine: TextToSpeech? = null
    private var ttsReady = false

    companion object {
        private const val CHANNEL_ID = "pydroidlab_python_notify"
    }

    init {
        createNotificationChannel()
    }

    @Keep
    fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    @Keep
    fun vibrate(durationMs: Int) {
        val safeMs = durationMs.toLong().coerceIn(10L, 5000L)
        runCatching {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.vibrate(VibrationEffect.createOneShot(safeMs, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    @Keep
    fun sendNotification(title: String, message: String) {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            nm.notify(notificationIdCounter.getAndIncrement(), notification)
        }
    }

    @Keep
    fun getClipboardText(): String {
        val ref = AtomicReference("")
        val latch = CountDownLatch(1)
        mainHandler.post {
            runCatching {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val item = cm?.primaryClip?.getItemAt(0)
                ref.set(item?.coerceToText(context)?.toString() ?: "")
            }
            latch.countDown()
        }
        latch.await(1, TimeUnit.SECONDS)
        return ref.get()
    }

    @Keep
    fun setClipboardText(text: String) {
        val latch = CountDownLatch(1)
        mainHandler.post {
            runCatching {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("PyDroidLab", text))
            }
            latch.countDown()
        }
        latch.await(1, TimeUnit.SECONDS)
    }

    @Keep
    fun speakText(text: String) {
        mainHandler.post {
            if (ttsEngine == null) {
                ttsEngine = TextToSpeech(context.applicationContext) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        ttsReady = true
                        ttsEngine?.language = Locale.getDefault()
                        ttsEngine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pydroid_tts")
                    }
                }
            } else if (ttsReady) {
                ttsEngine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pydroid_tts")
            }
        }
    }

    @Keep
    fun getDeviceInfoJson(): String {
        return runCatching {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            val json = JSONObject().apply {
                put("manufacturer", Build.MANUFACTURER)
                put("brand", Build.BRAND)
                put("model", Build.MODEL)
                put("android_release", Build.VERSION.RELEASE)
                put("sdk_int", Build.VERSION.SDK_INT)
                put("supported_abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
                put("battery_percent", batteryPct)
            }
            json.toString()
        }.getOrDefault("{}")
    }

    @Keep
    fun showImagePreview(imagePath: String) {
        val file = File(imagePath)
        if (file.exists() && file.isFile) {
            mainHandler.post {
                onShowImageOnUi(file)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PyDroidLab 脚本通知",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "由 Python 脚本通过 android_app.notify() 触发的通知"
            }
            nm.createNotificationChannel(channel)
        }
    }
}
