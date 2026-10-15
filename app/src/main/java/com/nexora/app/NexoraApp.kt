package com.nexora.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.nexora.app.data.Prefs
import com.nexora.app.data.tr

class NexoraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        val lang = Prefs(this).lang
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_NOTIF, tr(lang, "Notifications de scénarios", "إشعارات السيناريوهات"), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CH_REMINDER, tr(lang, "Rappels", "التذكيرات"), NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CH_SERVICE, tr(lang, "Moteur NEXORA", "محرك NEXORA"), NotificationManager.IMPORTANCE_LOW)
            )
        )
    }

    companion object {
        const val CH_NOTIF = "nexora_notif"
        const val CH_REMINDER = "nexora_reminder"
        const val CH_SERVICE = "nexora_service"
    }
}
