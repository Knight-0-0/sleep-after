package com.dafeiyu.sleepafter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import kotlin.math.sqrt

/**
 * 守夜服务：熄屏 + 持续静止 => 判定入睡 => 设置"睡够 N 小时"的系统闹钟。
 *
 * 为什么不用手环的数据：
 * 小米/红米的 Vela 快应用读不到心率，且手环的睡眠记录是"睡醒后回传"，
 * 拿不到入睡那一刻的实时信号。手机加速度计反而是最可靠、零延迟的来源。
 */
class SleepWatchService : Service(), SensorEventListener {

    companion object {
        const val ACTION_ARM = "com.dafeiyu.sleepafter.ARM"
        const val ACTION_DISARM = "com.dafeiyu.sleepafter.DISARM"

        private const val CH_WATCH = "sleepafter_watch"
        private const val NOTI_WATCH = 2001

        /** 计算"静止程度"的滑动窗口 */
        private const val WINDOW_MS = 30_000L

        /** 窗口内加速度模长的标准差低于这个值，认为是静止躺着 */
        private const val STILL_SD = 0.035

        /** 最多守夜 3 小时，超时自动放弃，避免整夜耗电 */
        private const val MAX_WATCH_MS = 3 * 60 * 60 * 1000L

        fun start(c: Context) {
            val i = Intent(c, SleepWatchService::class.java).setAction(ACTION_ARM)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) c.startForegroundService(i)
            else c.startService(i)
        }

        fun stop(c: Context) {
            val i = Intent(c, SleepWatchService::class.java).setAction(ACTION_DISARM)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) c.startForegroundService(i)
            else c.startService(i)
        }
    }

    private lateinit var sensorManager: SensorManager
    private lateinit var powerManager: PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    private val samples = ArrayDeque<Pair<Long, Float>>()
    private var screenOff = false
    private var stillSince = 0L
    private var onsetDone = false
    private var running = false
    private var receiverRegistered = false
    private var armedElapsed = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> screenOff = true
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    // 只要亮屏就重置计时：躺床上刷手机不算睡着
                    screenOff = false
                    stillSince = 0L
                    samples.clear()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SensorManager::class.java)
        powerManager = getSystemService(PowerManager::class.java)
        screenOff = !powerManager.isInteractive

        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        })
        receiverRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISARM) {
            // 无论从哪儿启动的，都先满足"5 秒内进前台"的要求，避免 ForegroundServiceDidNotStartInTime
            runCatching { startForeground(NOTI_WATCH, buildWatchNotification()) }
            Store.clearSession(this)
            AlarmScheduler.cancel(this)
            cleanup()
            stopSelf()
            return START_NOT_STICKY
        }

        // 已经被系统重启过一次，且已经有待响闹钟：不重复守夜
        if (Store.triggerAt(this) > System.currentTimeMillis()) {
            cleanup()
            stopSelf()
            return START_NOT_STICKY
        }

        begin()
        return START_STICKY
    }

    private fun begin() {
        if (running) return
        running = true

        // 重置本次守夜的状态（服务实例可能被复用）
        onsetDone = false
        stillSince = 0L
        samples.clear()
        armedElapsed = SystemClock.elapsedRealtime()

        startForeground(NOTI_WATCH, buildWatchNotification())

        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SleepAfter::watch"
        ).apply {
            setReferenceCounted(false)
            acquire(MAX_WATCH_MS)
        }

        val acc = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (acc == null) {
            // 极端情况：没有加速度计，直接按"现在"起算，别让用户没闹钟
            onSleepOnset()
            return
        }
        sensorManager.registerListener(this, acc, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (onsetDone) return
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val now = SystemClock.elapsedRealtime()

        // 超过最大守夜时间，放弃（不设闹钟，避免半夜乱响）
        if (now - armedElapsed > MAX_WATCH_MS) {
            Store.clearSession(this)
            cleanup()
            stopSelf()
            return
        }

        val mag = sqrt(
            event.values[0] * event.values[0] +
                event.values[1] * event.values[1] +
                event.values[2] * event.values[2]
        )

        samples.addLast(now to mag)
        while (samples.isNotEmpty() && now - samples.first().first > WINDOW_MS) {
            samples.removeFirst()
        }

        if (!screenOff) {
            stillSince = 0L
            return
        }
        if (samples.size < 10) return

        val mean = samples.sumOf { it.second.toDouble() } / samples.size
        var variance = 0.0
        for (s in samples) {
            val d = s.second - mean
            variance += d * d
        }
        val sd = sqrt(variance / samples.size)

        if (sd < STILL_SD) {
            if (stillSince == 0L) stillSince = now
        } else {
            stillSince = 0L
        }

        val stillMs = if (stillSince == 0L) 0L else now - stillSince
        if (stillMs >= Store.onsetStrictMinutes(this) * 60_000L) {
            onSleepOnset()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun onSleepOnset() {
        if (onsetDone) return
        onsetDone = true

        val now = System.currentTimeMillis()
        val target = Store.targetMinutes(this) * 60_000L
        val trigger = now + target

        Store.setOnsetAt(this, now)
        Store.setTriggerAt(this, trigger)

        val armed = Store.armedAt(this)
        val latency = if (armed > 0) ((now - armed) / 60_000L).toInt() else 0
        if (armed > 0) {
            Store.setLastLatencyMin(this, latency)
        }
        Store.addRecord(this, now, latency, Store.targetMinutes(this))

        AlarmScheduler.schedule(this, trigger)
        notifyOnset(trigger)
        cleanup()
        stopSelf()
    }

    private fun buildWatchNotification(): Notification {
        ensureChannel()
        val open = PendingIntent.getActivity(
            this, 4301, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancel = PendingIntent.getService(
            this, 4302,
            Intent(this, SleepWatchService::class.java).setAction(ACTION_DISARM),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CH_WATCH)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("正在等你睡着")
            .setContentText("熄屏并静止 ${Store.onsetStrictMinutes(this)} 分钟后开始计时")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel, "取消", cancel
                ).build()
            )
            .build()
    }

    private fun notifyOnset(trigger: Long) {
        ensureChannel()
        val open = PendingIntent.getActivity(
            this, 4303, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val fmt = android.text.format.DateFormat.getTimeFormat(this)
        val text = "已判定入睡，闹钟 " + fmt.format(java.util.Date(trigger))
        val n = Notification.Builder(this, CH_WATCH)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("计时开始")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTI_WATCH + 1, n)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CH_WATCH, "守夜状态", NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                setSound(null, null)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun cleanup() {
        running = false
        onsetDone = true
        runCatching { sensorManager.unregisterListener(this) }
        if (receiverRegistered) {
            runCatching { unregisterReceiver(screenReceiver) }
            receiverRegistered = false
        }
        wakeLock?.let {
            if (it.isHeld) runCatching { it.release() }
        }
        wakeLock = null
        runCatching { stopForeground(true) }
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
