package com.example.progettoappiot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Worker eseguito in background ogni 15 minuti (minimo Android WorkManager).
 * Controlla se ci sono nuovi accessi e manda una notifica locale all'admin.
 * NON richiede Firebase o servizi esterni.
 */
class DoorPollingWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    companion object {
        const val CHANNEL_ID = "doormotic_door_events"
        const val PREF_LAST_COUNT = "last_access_count"
        const val NOTIFICATION_ID = 1001
    }

    override fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("doormotic_prefs", Context.MODE_PRIVATE)

        // Solo l'admin riceve notifiche
        val isAdmin = prefs.getBoolean("is_admin", false)
        if (!isAdmin) return Result.success()

        return try {
            val response = RetrofitClient.getInstance(applicationContext)
                .getAccessiCount()
                .execute()

            if (!response.isSuccessful) return Result.retry()

            val newCount = response.body()?.get("count") ?: 0
            val lastCount = prefs.getInt(PREF_LAST_COUNT, -1)

            when {
                lastCount == -1 -> {
                    // Prima esecuzione: salva il conteggio attuale senza notificare
                    prefs.edit().putInt(PREF_LAST_COUNT, newCount).apply()
                }
                newCount > lastCount -> {
                    val diff = newCount - lastCount
                    prefs.edit().putInt(PREF_LAST_COUNT, newCount).apply()
                    sendNotification(diff)
                }
            }
            Result.success()

        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun sendNotification(newCount: Int) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel(manager)

        val msg = if (newCount == 1) "1 nuovo accesso alla porta" else "$newCount nuovi accessi alla porta"

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("DOORmotic 🚪")
            .setContentText(msg)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Eventi Porta DOORmotic",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avvisi quando qualcuno apre o chiude la porta"
            }
            manager.createNotificationChannel(channel)
        }
    }
}
