package com.dafeiyu.sleepafter

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 入睡记录：把最近 60 天的"从点开始到判定睡着"画成一张列表。
 */
class HistoryActivity : Activity() {

    private val dateFmt = SimpleDateFormat("M月d日 E", Locale.CHINA)
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        findViewById<View>(R.id.backBtn).setOnClickListener { finishWithAnim() }

        val records = Store.history(this)
        val host = findViewById<LinearLayout>(R.id.listHost)
        val empty = findViewById<View>(R.id.emptyView)
        val subtitle = findViewById<TextView>(R.id.subtitle)

        if (records.isEmpty()) {
            host.visibility = View.GONE
            empty.visibility = View.VISIBLE
        } else {
            val avg = records.sumOf { it.latencyMin } / records.size
            subtitle.text = "共 ${records.size} 晚 · 平均 $avg 分钟入睡"
            for ((i, r) in records.withIndex()) {
                if (i > 0) host.addView(divider())
                host.addView(row(r))
            }
        }

        startEntrance()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(s: String, sizeSp: Float, colorRes: Int, medium: Boolean = false): TextView {
        return TextView(this).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(getColor(colorRes))
            if (medium) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
    }

    private fun row(r: Store.Record): View {
        val wrap = LinearLayout.LayoutParams.MATCH_PARENT
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(wrap, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(label(dateFmt.format(Date(r.onsetAt)), 13f, R.color.text, true))
        val sub = label(
            "${timeFmt.format(Date(r.onsetAt))} 入睡 · 目标 ${targetLabel(r.targetMin)}",
            11f, R.color.muted
        )
        sub.setPadding(0, dp(3), 0, 0)
        left.addView(sub)

        row.addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(label("${r.latencyMin} 分", 14f, R.color.accent, true))
        return row
    }

    private fun divider(): View {
        return View(this).apply {
            setBackgroundColor(0x12FFFFFF)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            ).apply {
                marginStart = dp(16)
                marginEnd = dp(16)
            }
        }
    }

    private fun targetLabel(min: Int): String = when {
        min < 60 -> "$min 分钟"
        min % 60 == 0 -> "${min / 60} 小时"
        else -> "${min / 60} 小时 ${min % 60} 分"
    }

    private fun startEntrance() {
        val content = findViewById<View>(R.id.content)
        content.alpha = 0f
        content.translationY = 36f
        content.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(420L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun finishWithAnim() {
        finish()
        overridePendingTransition(R.anim.hold_in, R.anim.slide_down_out)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finishWithAnim()
    }
}
