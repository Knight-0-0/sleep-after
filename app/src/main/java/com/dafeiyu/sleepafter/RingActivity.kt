package com.dafeiyu.sleepafter

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

class RingActivity : Activity() {

    private lateinit var content: View
    private lateinit var glow: View
    private lateinit var hold: HoldRingView
    private var closed = false

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

        content = findViewById(R.id.content)
        glow = findViewById(R.id.glow)
        hold = findViewById(R.id.hold)

        findViewById<TextView>(R.id.clock).text =
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        hold.onComplete = { closeAlarm() }
        findViewById<View>(R.id.snooze).setOnClickListener { snooze() }

        startPulse()
        startEntrance()
    }

    private fun startPulse() {
        ObjectAnimator.ofFloat(glow, View.ALPHA, 0.45f, 1f).apply {
            duration = 880L
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun startEntrance() {
        val clock = findViewById<View>(R.id.clock)
        clock.alpha = 0f
        clock.scaleX = 0.84f
        clock.scaleY = 0.84f
        clock.translationY = 26f
        clock.animate()
            .alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
            .setDuration(520L)
            .setInterpolator(OvershootInterpolator(0.9f))
            .start()

        hold.alpha = 0f
        hold.scaleX = 0.72f
        hold.scaleY = 0.72f
        hold.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(200L)
            .setDuration(560L)
            .setInterpolator(OvershootInterpolator(1.1f))
            .start()

        val snooze = findViewById<View>(R.id.snooze)
        snooze.alpha = 0f
        snooze.translationY = 18f
        snooze.animate()
            .alpha(1f).translationY(0f)
            .setStartDelay(420L)
            .setDuration(360L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun closeAlarm() {
        if (closed) return
        closed = true
        AlarmService.stop(this)
        hold.animate().scaleX(1.18f).scaleY(1.18f).alpha(0f).setDuration(260L).start()
        glow.animate().alpha(0f).setDuration(240L).start()
        content.animate()
            .scaleX(0.9f).scaleY(0.9f).alpha(0f)
            .setDuration(300L)
            .setStartDelay(60L)
            .setInterpolator(DecelerateInterpolator())
            .start()
        content.postDelayed({ finishWithAnim() }, 330L)
    }

    private fun snooze() {
        if (closed) return
        closed = true
        AlarmService.stop(this)
        val at = System.currentTimeMillis() + 9 * 60_000L
        Store.setOnsetAt(this, 0L)
        Store.setTriggerAt(this, at)
        AlarmScheduler.schedule(this, at)
        content.animate()
            .translationYBy(40f).alpha(0f)
            .setDuration(260L)
            .setInterpolator(DecelerateInterpolator())
            .start()
        content.postDelayed({ finishWithAnim() }, 260L)
    }

    private fun finishWithAnim() {
        finish()
        overridePendingTransition(R.anim.hold_in, R.anim.ring_out)
    }

    override fun onDestroy() {
        if (isFinishing) AlarmService.stop(this)
        super.onDestroy()
    }
}

class HoldRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var holdMillis: Long = 3000L
    var onComplete: (() -> Unit)? = null

    private var progress = 0f
    private var holding = false
    private var completed = false
    private var animator: ValueAnimator? = null

    private fun dp(v: Float) = v * resources.displayMetrics.density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(7f)
        strokeCap = Paint.Cap.ROUND
        color = 0x1FFFFFFF
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(7f)
        strokeCap = Paint.Cap.ROUND
        color = 0xFF9DC1FF.toInt()
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x00000000
    }

    private val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(17f)
        color = 0xFFF2F6FF.toInt()
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(11f)
        color = 0xFF8C97AD.toInt()
    }

    init {
        isClickable = true
        isFocusable = true
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(cx, cy) - dp(16f)

        if (progress > 0f) {
            fillPaint.color = ((progress * 30).toInt() shl 24) or 0x009DC1FF
            canvas.drawCircle(cx, cy, radius - dp(6f), fillPaint)
        }

        canvas.drawCircle(cx, cy, radius, trackPaint)

        if (progress > 0f) {
            val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
            canvas.drawArc(rect, -90f, 357f * progress, false, arcPaint)
        }

        if (completed) {
            canvas.drawText("好", cx, cy + dp(8f), mainPaint)
        } else {
            canvas.drawText("长按", cx, cy + dp(4f), mainPaint)
            canvas.drawText("3 秒关闭", cx, cy + dp(24f), subPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                beginHold()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                releaseHold()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun beginHold() {
        if (completed) return
        holding = true
        animate().scaleX(0.96f).scaleY(0.96f).setDuration(120L).start()
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

        animator?.cancel()
        val remaining = ((1f - progress) * holdMillis).toLong().coerceAtLeast(120L)
        animator = ValueAnimator.ofFloat(progress, 1f).apply {
            duration = remaining
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (holding && progress >= 0.999f) {
                        holding = false
                        completed = true
                        invalidate()
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onComplete?.invoke()
                    }
                }
            })
            start()
        }
    }

    private fun releaseHold() {
        if (!holding) return
        holding = false
        animate().scaleX(1f).scaleY(1f).setDuration(160L).start()
        animator?.cancel()
        animator = null
        ValueAnimator.ofFloat(progress, 0f).apply {
            duration = 220L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
}
