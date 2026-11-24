package com.dafeiyu.sleepafter

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private val tick = Handler(Looper.getMainLooper())
    private val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    private lateinit var targetView: TextView
    private lateinit var statusView: TextView
    private lateinit var latencyView: TextView
    private lateinit var armBtn: Button
    private lateinit var cancelBtn: Button

    private val refresher = object : Runnable {
        override fun run() {
            refresh()
            tick.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        targetView = findViewById(R.id.target)
        statusView = findViewById(R.id.status)
        latencyView = findViewById(R.id.latency)
        armBtn = findViewById(R.id.arm)
        cancelBtn = findViewById(R.id.cancel)

        findViewById<Button>(R.id.minus15).setOnClickListener { changeTarget(-15) }
        findViewById<Button>(R.id.plus15).setOnClickListener { changeTarget(15) }
        findViewById<Button>(R.id.strictBtn).setOnClickListener { cycleStrict() }
        findViewById<Button>(R.id.exactPerm).setOnClickListener {
            AlarmScheduler.requestExactPermission(this)
        }
        findViewById<Button>(R.id.battPerm).setOnClickListener { requestBattery() }

        armBtn.setOnClickListener { arm() }
        cancelBtn.setOnClickListener { disarm() }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11)
        }
    }

    override fun onResume() {
        super.onResume()
        tick.post(refresher)
    }

    override fun onPause() {
        super.onPause()
        tick.removeCallbacks(refresher)
    }

    private fun changeTarget(delta: Int) {
        val v = (Store.targetMinutes(this) + delta).coerceIn(30, 720)
        Store.setTargetMinutes(this, v)
        refresh()
    }

    private fun cycleStrict() {
        val seq = listOf(5, 8, 12, 15, 20)
        val cur = Store.onsetStrictMinutes(this)
        val next = seq[(seq.indexOf(cur).coerceAtLeast(0) + 1) % seq.size]
        Store.setOnsetStrictMinutes(this, next)
        refresh()
    }

    private fun arm() {
        if (!AlarmScheduler.canScheduleExact(this)) {
            Toast.makeText(this, "先打开「闹钟和提醒」权限，否则到点不会响", Toast.LENGTH_LONG).show()
            AlarmScheduler.requestExactPermission(this)
            return
        }
        Store.setArmedAt(this, System.currentTimeMillis())
        Store.setOnsetAt(this, 0L)
        Store.setTriggerAt(this, 0L)
        AlarmScheduler.cancel(this)
        SleepWatchService.start(this)
        Toast.makeText(this, "开始守夜，把手机放下就行", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun disarm() {
        SleepWatchService.stop(this)
        AlarmScheduler.cancel(this)
        Store.clearSession(this)
        Toast.makeText(this, "已取消", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun requestBattery() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, "已经在电池白名单里了", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
    }

    private fun refresh() {
        val target = Store.targetMinutes(this)
        targetView.text = String.format(Locale.getDefault(), "%d 小时 %02d 分", target / 60, target % 60)
        findViewById<Button>(R.id.strictBtn).text =
            "静止 ${Store.onsetStrictMinutes(this)} 分钟算睡着"

        val now = System.currentTimeMillis()
        val armed = Store.armedAt(this)
        val onset = Store.onsetAt(this)
        val trigger = Store.triggerAt(this)

        val watching = armed > 0L && trigger == 0L
        val pending = trigger > now

        statusView.text = when {
            onset > 0L && pending ->
                "已判定入睡 ${fmt.format(Date(onset))}\n闹钟 ${fmt.format(Date(trigger))}"
            watching ->
                "守夜中…\n熄屏并静止 ${Store.onsetStrictMinutes(this)} 分钟后开始计时"
            else -> "未启动"
        }

        val lat = Store.lastLatencyMin(this)
        latencyView.text =
            if (lat >= 0) "上次入睡潜伏期：$lat 分钟" else "还没有记录，先跑一晚"

        armBtn.isEnabled = !watching
        armBtn.text = if (watching) "守夜中…" else "开始守夜"
        cancelBtn.visibility = if (watching || pending) View.VISIBLE else View.GONE

        val exactOk = AlarmScheduler.canScheduleExact(this)
        findViewById<Button>(R.id.exactPerm).text =
            if (exactOk) "精确闹钟权限 ✓" else "打开精确闹钟权限（必做）"
    }
}
