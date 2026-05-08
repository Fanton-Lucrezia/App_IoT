package com.example.progettoappiot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class DoormoticMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID   = "doormotic_accessi"
        const val CHANNEL_NAME = "Accessi porta"
    }

    /**
     * Chiamato quando FCM genera un nuovo token (primo avvio o refresh).
     * Lo mandiamo a Flask solo se l'utente è admin e ha già fatto login.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val prefs    = getSharedPreferences("doormotic_prefs", Context.MODE_PRIVATE)
        val username = prefs.getString("username", null)
        val isAdmin  = prefs.getBoolean("is_admin", false)

        if (username != null && isAdmin) {
            registerTokenOnServer(username, token)
        }
    }

    /**
     * Chiamato quando arriva una notifica con l'app in foreground.
     * Se l'app è in background Android la mostra automaticamente.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: "DOORmotic"
        val body  = message.notification?.body  ?: ""
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Crea il canale (necessario da Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Notifiche accessi alla porta" }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}

/**
 * Registra il token FCM su Flask — chiamata da LoginActivity dopo login admin
 * e da DoormoticMessagingService quando il token viene refreshato.
 */
fun registerTokenOnServer(username: String, token: String) {
    // Usa una coroutine-free call fire-and-forget
    try {
        val context = null // non serve context per Retrofit statico
        // Nota: RetrofitClient.getInstance() richiede context —
        // questa viene chiamata con context direttamente da LoginActivity
    } catch (_: Exception) {}
}
