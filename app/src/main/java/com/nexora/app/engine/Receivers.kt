package com.nexora.app.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nexora.app.data.AppDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("id", -1L)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val s = AppDb.get(context).scenarios().get(id)
                if (s != null) {
                    Engine.run(context, s, false)
                    Scheduler.schedule(context, s) // prochaine occurrence
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Scheduler.rescheduleAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}
