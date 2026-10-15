package com.nexora.app.engine

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nexora.app.NexoraApp
import com.nexora.app.R
import com.nexora.app.data.AppDb
import com.nexora.app.data.LogEntry
import com.nexora.app.data.Prefs
import com.nexora.app.data.Scenario
import com.nexora.app.ui.MainActivity
import java.util.Calendar

/** Exécuteur : vérifie protections et conditions, exécute l'action, journalise. */
object Engine {
    /** Protection anti-boucle : un même scénario ne s'exécute pas deux fois en moins de 30 s. */
    const val MIN_INTERVAL_MS = 30_000L

    /** Retourne le code de résultat (OK, KILL, RATE, COND, NO_NOTIF, ERROR:...). */
    suspend fun run(ctx: Context, s: Scenario, manual: Boolean): String {
        val db = AppDb.get(ctx)
        val now = System.currentTimeMillis()
        val (status, detail) = when {
            Prefs(ctx).kill -> "SKIPPED" to "KILL"
            !manual && now - s.lastRunAt < MIN_INTERVAL_MS -> "SKIPPED" to "RATE"
            !evaluate(ctx, s) -> "SKIPPED" to "COND"
            else -> try {
                perform(ctx, s)
                db.scenarios().setLastRun(s.id, now)
                "SUCCESS" to "OK"
            } catch (e: SecurityException) {
                "ERROR" to "NO_NOTIF"
            } catch (e: Exception) {
                "ERROR" to ("ERROR:" + (e.message ?: e.javaClass.simpleName))
            }
        }
        db.logs().insert(LogEntry(scenarioId = s.id, scenarioName = s.name, ts = now, status = status, detail = detail))
        db.logs().trim()
        return detail
    }

    private fun evaluate(ctx: Context, s: Scenario): Boolean {
        val checks = buildList<Boolean> {
            s.condBatteryBelow?.let { add(batteryLevel(ctx) < it) }
            s.condCharging?.let { add(isCharging(ctx) == it) }
            s.condWifi?.let { add(onWifi(ctx) == it) }
        }
        if (checks.isEmpty()) return true
        return if (s.logic == "OR") checks.any { it } else checks.all { it }
    }

    private fun batteryIntent(ctx: Context): Intent? =
        ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    fun batteryLevel(ctx: Context): Int {
        val i = batteryIntent(ctx) ?: return 100
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return if (level < 0 || scale <= 0) 100 else level * 100 / scale
    }

    fun isCharging(ctx: Context): Boolean {
        val status = batteryIntent(ctx)?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: return false
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun onWifi(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    @SuppressLint("MissingPermission")
    private fun perform(ctx: Context, s: Scenario) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) throw SecurityException("notifications")
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val reminder = s.actionType == "REMINDER"
        val n = NotificationCompat.Builder(ctx, if (reminder) NexoraApp.CH_REMINDER else NexoraApp.CH_NOTIF)
            .setSmallIcon(R.drawable.ic_stat_nexora)
            .setContentTitle(s.title)
            .setContentText(s.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(s.message))
            .setContentIntent(open)
            .setAutoCancel(!reminder)
            .setCategory(if (reminder) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_STATUS)
            .setPriority(if (reminder) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .build()
        nm.notify(s.id.toInt(), n)
    }
}

/** Planification des déclencheurs horaires via AlarmManager. */
object Scheduler {
    fun nextTime(s: Scenario, from: Calendar = Calendar.getInstance()): Long? {
        for (offset in 0..7) {
            val c = from.clone() as Calendar
            c.add(Calendar.DAY_OF_YEAR, offset)
            c.set(Calendar.HOUR_OF_DAY, s.hour)
            c.set(Calendar.MINUTE, s.minute)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            if (c.timeInMillis <= from.timeInMillis) continue
            val dayIdx = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 // lundi = 0
            if ((s.daysMask and (1 shl dayIdx)) != 0) return c.timeInMillis
        }
        return null
    }

    private fun pending(ctx: Context, id: Long): PendingIntent = PendingIntent.getBroadcast(
        ctx, id.toInt(),
        Intent(ctx, AlarmReceiver::class.java).putExtra("id", id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun cancel(ctx: Context, id: Long) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx, id))
    }

    fun schedule(ctx: Context, s: Scenario) {
        cancel(ctx, s.id)
        if (Prefs(ctx).kill || s.triggerType != "TIME" || !s.enabled) return
        val t = nextTime(s) ?: return
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pending(ctx, s.id)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
        }
    }

    suspend fun rescheduleAll(ctx: Context) {
        AppDb.get(ctx).scenarios().all().forEach { schedule(ctx, it) }
        EventService.sync(ctx)
    }
}
