package com.dafeiyu.sleepafter

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * 用系统闹钟通道（setAlarmClock）而不是普通定时器：
 * - 不受 Doze / 省电策略延迟，到点必响
 * - 系统通知栏会显示闹钟图标，能被"小米运动健康 / Gadgetbridge"同步到手表震动
 */
object AlarmScheduler {

    const val ACTION_RING = "com.dafeiyu.sleepafter.RING"

    private const val REQ_RING = 4101
    private const val REQ_SHOW = 4102

    fun canScheduleExact(c: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        else true

    fun requestExactPermission(c: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                c.startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + c.packageName)
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    private fun ringIntent(c: Context): PendingIntent =
        PendingIntent.getBroadcast(
            c,
            REQ_RING,
            Intent(c, AlarmReceiver::class.java).setAction(ACTION_RING),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun showIntent(c: Context): PendingIntent =
        PendingIntent.getActivity(
            c,
            REQ_SHOW,
            Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun schedule(c: Context, triggerAt: Long) {
        val am = c.getSystemService(AlarmManager::class.java)
        val info = AlarmManager.AlarmClockInfo(triggerAt, showIntent(c))
        try {
            am.setAlarmClock(info, ringIntent(c))
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, ringIntent(c))
        }
    }

    fun cancel(c: Context) {
        runCatching { c.getSystemService(AlarmManager::class.java).cancel(ringIntent(c)) }
    }
}
