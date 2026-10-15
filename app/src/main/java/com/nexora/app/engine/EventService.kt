package com.nexora.app.engine

import android.app.Notification
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.nexora.app.NexoraApp
import com.nexora.app.R
import com.nexora.app.data.AppDb
import com.nexora.app.data.Prefs
import com.nexora.app.data.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Service de premier plan, démarré uniquement s'il existe un scénario actif basé sur un événement
 * (Android n'autorise pas ces événements dans le manifeste).
 */
class EventService : Service() {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val type = when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> "POWER_CONNECTED"
                Intent.ACTION_POWER_DISCONNECTED -> "POWER_DISCONNECTED"
                Intent.ACTION_BATTERY_LOW -> "BATTERY_LOW"
                else -> return
            }
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    AppDb.get(context).scenarios().all()
                        .filter { it.enabled && it.triggerType == type }
                        .forEach { Engine.run(context, it, false) }
                } finally {
                    pending.finish()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val lang = Prefs(this).lang
        val n: Notification = NotificationCompat.Builder(this, NexoraApp.CH_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_nexora)
            .setContentTitle(tr(lang, "NEXORA est actif", "NEXORA نشط"))
            .setContentText(tr(lang, "Surveillance des événements du téléphone", "مراقبة أحداث الهاتف"))
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(this, 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_LOW)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private val EVENT_TYPES = setOf("POWER_CONNECTED", "POWER_DISCONNECTED", "BATTERY_LOW")

        /** Démarre ou arrête le service selon les scénarios actifs et l'arrêt général. */
        suspend fun sync(ctx: Context) {
            val need = !Prefs(ctx).kill &&
                AppDb.get(ctx).scenarios().all().any { it.enabled && it.triggerType in EVENT_TYPES }
            val i = Intent(ctx, EventService::class.java)
            if (need) {
                try { ContextCompat.startForegroundService(ctx, i) } catch (_: Exception) {}
            } else {
                ctx.stopService(i)
            }
        }
    }
}
