package com.dafeiyu.sleepafter

import android.content.Context

/**
 * 所有状态都存在 SharedPreferences 里：进程被系统杀掉也能恢复。
 */
object Store {

    private const val PREF = "sleepafter"

    /** 历史记录上限 */
    private const val MAX_RECORDS = 60
    private const val MAX_AGE_MS = 60L * 24 * 60 * 60 * 1000

    /** 一晚的记录：判定时刻 / 潜伏期（分钟）/ 当时设的目标时长（分钟） */
    data class Record(val onsetAt: Long, val latencyMin: Int, val targetMin: Int)

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

    /**
     * 历史记录，按时间从新到旧。用 "at,latency,target" 逐条拼接存字符串，
     * 不引入 JSON 依赖；超过 60 条或 60 天自动裁掉。
     */
    fun history(c: Context): List<Record> {
        val raw = sp(c).getString("history", "") ?: ""
        if (raw.isBlank()) return emptyList()
        val out = ArrayList<Record>()
        for (line in raw.split(";")) {
            if (line.isBlank()) continue
            val p = line.split(",")
            if (p.size < 3) continue
            val at = p[0].toLongOrNull() ?: continue
            out.add(Record(at, p[1].toIntOrNull() ?: 0, p[2].toIntOrNull() ?: 0))
        }
        out.sortByDescending { it.onsetAt }
        return out
    }

    fun addRecord(c: Context, onsetAt: Long, latencyMin: Int, targetMin: Int) {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        val list = ArrayList<Record>()
        list.add(Record(onsetAt, latencyMin, targetMin))
        for (r in history(c)) {
            if (r.onsetAt < cutoff) continue
            list.add(r)
        }
        while (list.size > MAX_RECORDS) list.removeAt(list.size - 1)
        val raw = list.joinToString(";") { "${it.onsetAt},${it.latencyMin},${it.targetMin}" }
        sp(c).edit().putString("history", raw).apply()
    }

    fun clearHistory(c: Context) = sp(c).edit().remove("history").apply()

    fun clearSession(c: Context) {
        sp(c).edit().remove("armedAt").remove("onsetAt").remove("triggerAt").apply()
    }
}
