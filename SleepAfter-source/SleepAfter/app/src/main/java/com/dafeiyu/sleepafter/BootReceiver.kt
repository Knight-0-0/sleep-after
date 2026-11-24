package com.dafeiyu.sleepafter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 重启 / 应用更新后，把还没响的闹钟重新挂回去 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val trigger = Store.triggerAt(context)
        val now = System.currentTimeMillis()
        if (trigger > now) {
            AlarmScheduler.schedule(context, trigger)
        } else if (trigger != 0L) {
            Store.clearSession(context)
        }
    }
}
