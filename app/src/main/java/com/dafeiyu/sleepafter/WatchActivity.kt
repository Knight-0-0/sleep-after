package com.dafeiyu.sleepafter

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 守夜页：手机放下之后的"陪伴界面"。
 * 月亮缓慢呼吸，中间是倒计时（还没判入睡时显示已等待多久）。
 */
class WatchActivity : Activity() {

    private val tick = Handler(Looper.getMainLooper())
    private val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    private lateinit var phase: TextView
    private lateinit var clock: TextView
    private lateinit var mode: TextView
    private lateinit var detail: TextView
    private lateinit var bar: ProgressBar

    private var breathing: AnimatorSet? = null

    private val refresher = object : Runnable {
        override fun run() {
            refresh()
            tick.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_watch)

        phase = findViewById(R.id.phase)
        clock = findViewById(R.id.clock)
        mode = findViewById(R.id.mode)
        detail = findViewById(R.id.detail)
        bar = findViewById(R.id.bar)

        findViewById<Button>(R.id.stopWatch).setOnClickListener {
            SleepWatchService.stop(this)
            AlarmScheduler.cancel(this)
            Store.clearSession(this)
            finish()
            overridePendingTransition(R.anim.hold_in, R.anim.slide_down_out)
        }

        startBreathing()
        startEntrance()
    }

    private fun startBreathing() {
        val glow = findViewById<View>(R.id.moonGlow)
        val moon = findViewById<View>(R.id.moon)
        val pulseAlpha = ObjectAnimator.ofFloat(glow, View.ALPHA, 0.45f, 1f)
        val pulseScaleX = ObjectAnimator.ofFloat(glow, View.SCALE_X, 0.94f, 1.08f)
        val pulseScaleY = ObjectAnimator.ofFloat(glow, View.SCALE_Y, 0.94f, 1.08f)
        val moonFloat = ObjectAnimator.ofFloat(moon, View.TRANSLATION_Y, 0f, -6f)
        for (a in arrayOf(pulseAlpha, pulseScaleX, pulseScaleY, moonFloat)) {
            a.duration = 2800L
            a.repeatCount = ObjectAnimator.INFINITE
            a.repeatMode = ObjectAnimator.REVERSE
            a.interpolator = AccelerateDecelerateInterpolator()
        }
        breathing = AnimatorSet().apply {
            playTogether(pulseAlpha, pulseScaleX, pulseScaleY, moonFloat)
            start()
        }
    }

    private fun startEntrance() {
        val content = findViewById<View>(R.id.watchContent)
        content.alpha = 0f
        content.translationY = 40f
        content.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(480L)
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

    override fun onDestroy() {
        breathing?.cancel()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finish()
        overridePendingTransition(R.anim.hold_in, R.anim.slide_down_out)
    }

    private fun refresh() {
        val now = System.currentTimeMillis()
        val armed = Store.armedAt(this)
        val onset = Store.onsetAt(this)
        val trigger = Store.triggerAt(this)
        val target = Store.targetMinutes(this)

        if (trigger > now) {
            val remain = trigger - now
            val total = target * 60_000L
            phase.text = if (onset > 0L) "已判定入睡 ${fmt.format(Date(onset))}" else "闹钟已设置"
            clock.text = hms(remain)
            mode.text = "距离闹钟"
            detail.text = "闹钟 ${fmt.format(Date(trigger))} · 睡满 $target 分钟"
            bar.progress = (((total - remain) * 100) / total).toInt().coerceIn(0, 100)
        } else if (armed > 0L) {
            val waited = now - armed
            phase.text = "等待入睡"
            clock.text = hms(waited)
            mode.text = "已等待"
            detail.text = "熄屏并静止 ${Store.onsetStrictMinutes(this)} 分钟后开始计时"
            bar.progress = 0
        } else {
            finish()
            return
        }
    }

    private fun hms(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0)
            String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, sec)
        else String.format(Locale.getDefault(), "%02d:%02d", m, sec)
    }
}
