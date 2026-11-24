package com.dafeiyu.sleepafter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 闹钟到点：发高优先级通知 + fullScreenIntent 拉起响铃界面。
 * Android 10+ 禁止后台直接 startActivity，fullScreenIntent 是唯一合规且可靠的方式。
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val CH_RING = "sleepafter_ring"
        private const val NOTI_RING = 3001
    }

    override fun onReceive(context: Context, intent: Intent) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CH_RING,
                "起床闹钟",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setDescription("到点响铃")
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(ch)
        }

        val full = PendingIntent.getActivity(
            context,
            4201,
            Intent(context, RingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val noti = Notification.Builder(context, CH_RING)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("时间到了")
            .setContentText("点这里进入，长按按钮才能关掉")
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(Notification.PRIORITY_MAX)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .build()

        nm.notify(NOTI_RING, noti)

        // 部分旧机型 / 已授权机型允许直接拉起，多一层保险
        runCatching {
            context.startActivity(
                Intent(context, RingActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }

        Store.clearSession(context)
    }
}
