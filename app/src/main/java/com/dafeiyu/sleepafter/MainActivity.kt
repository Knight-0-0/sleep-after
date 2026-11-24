package com.dafeiyu.sleepafter

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private val tick = Handler(Looper.getMainLooper())
    private val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    private lateinit var hoursView: TextView
    private lateinit var minsView: TextView
    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView
    private lateinit var latencyView: TextView
    private lateinit var strictValue: TextView
    private lateinit var exactValue: TextView
    private lateinit var battValue: TextView
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

        hoursView = findViewById(R.id.targetHours)
        minsView = findViewById(R.id.targetMins)
        statusTitle = findViewById(R.id.statusTitle)
        statusDetail = findViewById(R.id.statusDetail)
        latencyView = findViewById(R.id.latency)
        strictValue = findViewById(R.id.strictValue)
        exactValue = findViewById(R.id.exactValue)
        battValue = findViewById(R.id.battValue)
        armBtn = findViewById(R.id.arm)
        cancelBtn = findViewById(R.id.cancel)

        findViewById<View>(R.id.durationBox).setOnClickListener { showDurationPicker() }
        findViewById<View>(R.id.strictBtn).setOnClickListener { cycleStrict() }
        findViewById<View>(R.id.exactPerm).setOnClickListener {
            AlarmScheduler.requestExactPermission(this)
        }
        findViewById<View>(R.id.battPerm).setOnClickListener { requestBattery() }

        armBtn.setOnClickListener { arm() }
        cancelBtn.setOnClickListener { disarm() }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11)
        }

        startEntrance()
    }

    /** 首屏：内容轻轻上浮淡入，而不是"啪"地出现 */
    private fun startEntrance() {
        val content = findViewById<View>(R.id.content)
        content.alpha = 0f
        content.translationY = 36f
        content.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(460L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    override fun onResume() {
        super.onResume()
        tick.post(refresher)
    }

    override fun onPause() {
        super.onPause()
        tick.removeCallbacks(refresher)
    }

    /** 点大数字：直接选小时 + 分钟，不用一次 15 分钟地磨 */
    private fun showDurationPicker() {
        val total = Store.targetMinutes(this)
        val view = layoutInflater.inflate(R.layout.dialog_duration, null)
        val hp = view.findViewById<NumberPicker>(R.id.pickHours)
        val mp = view.findViewById<NumberPicker>(R.id.pickMins)

        hp.minValue = 0
        hp.maxValue = 12
        hp.value = (total / 60).coerceIn(0, 12)
        hp.wrapSelectorWheel = false

        mp.minValue = 0
        mp.maxValue = 11
        mp.displayedValues = Array(12) { String.format(Locale.getDefault(), "%02d", it * 5) }
        mp.value = ((total % 60) / 5).coerceIn(0, 11)
        mp.wrapSelectorWheel = false

        val dlg = AlertDialog.Builder(this).setView(view).create()
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        view.findViewById<TextView>(R.id.pickCancel).setOnClickListener { dlg.dismiss() }
        view.findViewById<TextView>(R.id.pickOk).setOnClickListener {
            var v = hp.value * 60 + mp.value * 5
            if (v < 30) v = 30
            Store.setTargetMinutes(this, v)
            dlg.dismiss()
            refresh()
        }
        dlg.show()
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
        startActivity(Intent(this, WatchActivity::class.java))
        overridePendingTransition(R.anim.slide_up_in, R.anim.hold_out)
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
        hoursView.text = (target / 60).toString()
        minsView.text = String.format(Locale.getDefault(), "%02d", target % 60)

        strictValue.text = "静止 ${Store.onsetStrictMinutes(this)} 分钟"

        val exactOk = AlarmScheduler.canScheduleExact(this)
        exactValue.text = if (exactOk) "已开启 ✓" else "去开启"

        val pm = getSystemService(PowerManager::class.java)
        val battOk = pm.isIgnoringBatteryOptimizations(packageName)
        battValue.text = if (battOk) "已加入 ✓" else "去设置"

        val now = System.currentTimeMillis()
        val armed = Store.armedAt(this)
        val onset = Store.onsetAt(this)
        val trigger = Store.triggerAt(this)

        val watching = armed > 0L && trigger == 0L
        val pending = trigger > now

        when {
            onset > 0L && pending -> {
                statusTitle.text = "已判定入睡 ${fmt.format(Date(onset))}"
                statusDetail.text = "闹钟 ${fmt.format(Date(trigger))}，睡满 $target 分钟"
            }
            watching -> {
                statusTitle.text = "守夜中…"
                statusDetail.text = "熄屏并静止 ${Store.onsetStrictMinutes(this)} 分钟后开始计时"
            }
            pending -> {
                statusTitle.text = "闹钟 ${fmt.format(Date(trigger))}"
                statusDetail.text = "等待响铃，长按 3 秒才能关掉"
            }
            else -> {
                statusTitle.text = "未启动"
                statusDetail.text = "睡前点一下「开始守夜」，然后放下手机。"
            }
        }

        val lat = Store.lastLatencyMin(this)
        latencyView.text =
            if (lat >= 0) "上次入睡潜伏期 $lat 分钟" else "还没有记录，先跑一晚"

        armBtn.isEnabled = !watching
        armBtn.alpha = if (watching) 0.45f else 1f
        armBtn.text = if (watching) "守夜中…" else "开始守夜"
        cancelBtn.visibility = if (watching || pending) View.VISIBLE else View.GONE
    }
}
