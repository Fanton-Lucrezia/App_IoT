package com.example.progettoappiot

import android.content.Context
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private var instance: ApiService? = null
    private var currentBaseUrl: String = ""

    private const val DEFAULT_URL = "https://doormotic.up.railway.app"

    fun buildBaseUrl(serverInput: String): String {
        val trimmed = serverInput.trim().trimEnd('/')
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> "$trimmed/"
            trimmed.isNotEmpty() -> "https://$trimmed/"
            else -> "$DEFAULT_URL/"
        }
    }

    fun getInstance(context: Context): ApiService {
        val prefs = context.getSharedPreferences("DOORmotic", Context.MODE_PRIVATE)
        // Legge "server_url" (chiave usata da SettingsActivity).
        // Fallback: prima controlla la vecchia chiave "server_ip", poi usa il default Railway.
        val serverInput = prefs.getString("server_url", null)
            ?: prefs.getString("server_ip", null)
            ?: DEFAULT_URL
        val baseUrl = buildBaseUrl(serverInput)

        if (instance == null || baseUrl != currentBaseUrl) {
            currentBaseUrl = baseUrl

            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            instance = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        }
        return instance!!
    }

    fun resetInstance() {
        instance = null
        currentBaseUrl = ""
    }
}
