package com.dafeiyu.sleepafter

import android.app.Activity
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 响铃界面：锁屏上直接全屏、强制点亮、最大音量、循环震动。
 * 只能用"长按"关掉，防半睡半醒时误触。
 */
class RingActivity : Activity() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_ring)

        findViewById<TextView>(R.id.clock).text =
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        val am = getSystemService(AudioManager::class.java)
        setVolumeControlStream(AudioManager.STREAM_ALARM)
        runCatching {
            am.setStreamVolume(
                AudioManager.STREAM_ALARM,
                am.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0
            )
        }

        startSound()
        startVibrate()

        findViewById<Button>(R.id.stop).setOnLongClickListener {
            stopAll()
            finish()
            true
        }

        findViewById<Button>(R.id.snooze).setOnClickListener {
            val at = System.currentTimeMillis() + 9 * 60_000L
            stopAll(clear = false)
            Store.setOnsetAt(this, 0L)
            Store.setTriggerAt(this, at)
            AlarmScheduler.schedule(this, at)
            finish()
        }
    }

    private fun startSound() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: return
        ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
            play()
        }
    }

    private fun startVibrate() {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        vibrator = v
        val pattern = longArrayOf(0, 800, 400, 800, 1200)
        runCatching {
            v.vibrate(VibrationEffect.createWaveform(pattern, 0))
        }
    }

    private fun stopAll(clear: Boolean = true) {
        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching { vibrator?.cancel() }
        vibrator = null
        if (clear) {
            Store.clearSession(this)
        }
    }

    override fun onDestroy() {
        stopAll()
        super.onDestroy()
    }
}
