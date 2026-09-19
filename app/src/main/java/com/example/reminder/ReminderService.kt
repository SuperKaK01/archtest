package com.example.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import java.util.Locale

class ReminderService : Service() {

    companion object {
        const val ACTION_STOP = "com.example.reminder.STOP"
        const val CHANNEL_ID = "reminder_channel"
        const val NOTIF_ID = 1001
        const val ROUNDS = 3
        const val ALERTS_PER_ROUND = 3
        const val ALERT_GAP_MS = 4_000L
        const val ROUND_GAP_MS = 60_000L
    }

    private lateinit var handler: Handler
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var message: String = "อย่าลืมทำสิ่งที่ตั้งใจไว้"
    private var round = 0
    private var alertInRound = 0
    private var stopped = false

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            stopSelfSafe()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        handler = Handler(Looper.getMainLooper())
        createChannel()
        val filter = IntentFilter(ACTION_STOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stopReceiver, filter)
        }
        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("th", "TH")
                ttsReady = true
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelfSafe()
            return START_NOT_STICKY
        }
        message = intent?.getStringExtra("message") ?: message
        startForeground(NOTIF_ID, buildNotification("กำลังเตือน: $message"))
        round = 0
        alertInRound = 0
        handler.post(alertRunnable)
        return START_STICKY
    }

    private val alertRunnable = object : Runnable {
        override fun run() {
            if (stopped) return
            if (round >= ROUNDS) {
                stopSelfSafe()
                return
            }
            fireAlert()
            alertInRound++
            if (alertInRound >= ALERTS_PER_ROUND) {
                round++
                alertInRound = 0
                if (round >= ROUNDS) {
                    handler.postDelayed({ stopSelfSafe() }, ALERT_GAP_MS)
                } else {
                    handler.postDelayed(this, ROUND_GAP_MS)
                }
            } else {
                handler.postDelayed(this, ALERT_GAP_MS)
            }
        }
    }

    private fun fireAlert() {
        val spoken = "อย่าลืมนะ $message"
        vibrate()
        speak(spoken)
    }

    private fun vibrate() {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        val pattern = longArrayOf(0, 500, 250, 500, 250, 800)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, -1)
        }
    }

    private fun speak(text: String) {
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reminder-${System.currentTimeMillis()}")
        } else {
            handler.postDelayed({ if (!stopped) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reminder") }, 800)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(CHANNEL_ID, "การแจ้งเตือน", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(false)
                setSound(null, null)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): android.app.Notification {
        val stopIntent = Intent(ACTION_STOP).setPackage(packageName)
        val stopPi = PendingIntent.getBroadcast(
            this, 200, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("อย่าลืมนะ!")
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "หยุด", stopPi)
            .build()
    }

    private fun stopSelfSafe() {
        if (stopped) return
        stopped = true
        handler.removeCallbacksAndMessages(null)
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopped = true
        try { unregisterReceiver(stopReceiver) } catch (_: Exception) {}
        handler.removeCallbacksAndMessages(null)
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        super.onDestroy()
    }
}
