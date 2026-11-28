package com.dafeiyu.sleepafter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Store.clearSession(context)
        AlarmService.start(context)
    }
}

class AlarmService : Service() {

    companion object {
        private const val CH_RING = "sleepafter_ring"
        private const val NOTI_RING = 3001

        @Volatile
        var ringing = false
            private set

        fun start(c: Context) {
            val i = Intent(c, AlarmService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) c.startForegroundService(i)
            else c.startService(i)
        }

        fun stop(c: Context) {
            runCatching { c.stopService(Intent(c, AlarmService::class.java)) }
        }
    }

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTI_RING, buildNotification())
        if (!ringing) {
            ringing = true
            startSound()
            startVibrate()
        }
        return START_STICKY
    }

    private fun startSound() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: return

        val am = getSystemService(AudioManager::class.java)
        runCatching {
            am.setStreamVolume(
                AudioManager.STREAM_ALARM,
                am.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0
            )
        }

        runCatching {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.setDataSource(this, uri)
            mp.isLooping = true
            mp.prepare()
            mp.start()
            player = mp
        }
    }

    private fun startVibrate() {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        vibrator = v
        runCatching {
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500, 700, 1000), 0))
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CH_RING, "起床闹钟", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "到点响铃"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        ensureChannel()
        val open = PendingIntent.getActivity(
            this,
            4201,
            Intent(this, RingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CH_RING)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("时间到了")
            .setContentText("长按圆环 3 秒关闭闹钟")
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(Notification.PRIORITY_MAX)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(open, true)
            .setContentIntent(open)
            .build()
    }

    override fun onDestroy() {
        ringing = false
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        runCatching { vibrator?.cancel() }
        vibrator = null
        runCatching { stopForeground(true) }
        runCatching { getSystemService(NotificationManager::class.java).cancel(NOTI_RING) }
        super.onDestroy()
    }
}
