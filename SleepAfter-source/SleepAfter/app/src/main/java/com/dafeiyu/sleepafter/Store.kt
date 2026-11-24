package com.dafeiyu.sleepafter

import android.content.Context

/**
 * 所有状态都存在 SharedPreferences 里：进程被系统杀掉也能恢复。
 */
object Store {

    private const val PREF = "sleepafter"

    private fun sp(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** 目标睡眠时长（分钟），从"判定入睡"那一刻起算 */
    fun targetMinutes(c: Context): Int = sp(c).getInt("target", 480)
    fun setTargetMinutes(c: Context, v: Int) = sp(c).edit().putInt("target", v).apply()

    /** 连续静止多少分钟算睡着 */
    fun onsetStrictMinutes(c: Context): Int = sp(c).getInt("strict", 8)
    fun setOnsetStrictMinutes(c: Context, v: Int) = sp(c).edit().putInt("strict", v).apply()

    /** 用户按"开始守夜"的时刻 */
    fun armedAt(c: Context): Long = sp(c).getLong("armedAt", 0L)
    fun setArmedAt(c: Context, v: Long) = sp(c).edit().putLong("armedAt", v).apply()

    /** 判定为入睡的时刻 */
    fun onsetAt(c: Context): Long = sp(c).getLong("onsetAt", 0L)
    fun setOnsetAt(c: Context, v: Long) = sp(c).edit().putLong("onsetAt", v).apply()

    /** 闹钟实际响铃的时刻 */
    fun triggerAt(c: Context): Long = sp(c).getLong("triggerAt", 0L)
    fun setTriggerAt(c: Context, v: Long) = sp(c).edit().putLong("triggerAt", v).apply()

    /** 上一次的入睡潜伏期（分钟），-1 表示没有记录 */
    fun lastLatencyMin(c: Context): Int = sp(c).getInt("latency", -1)
    fun setLastLatencyMin(c: Context, v: Int) = sp(c).edit().putInt("latency", v).apply()

    fun clearSession(c: Context) {
        sp(c).edit().remove("armedAt").remove("onsetAt").remove("triggerAt").apply()
    }
}
