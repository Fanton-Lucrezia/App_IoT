package com.example.progettoappiot

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SettingsActivity : AppCompatActivity() {

    companion object {
        const val DEFAULT_SERVER_URL = "https://doormotic.up.railway.app"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<Toolbar>(R.id.settingsToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val prefs      = getSharedPreferences("DOORmotic", MODE_PRIVATE)
        val isAdmin    = prefs.getBoolean("is_admin", false)
        val currentUrl = prefs.getString("server_url", DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        val appVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"

        val etServerUrl  = findViewById<TextInputEditText>(R.id.etServerUrl)
        val btnTestConn  = findViewById<MaterialButton>(R.id.btnTestConnection)
        val btnSaveUrl   = findViewById<MaterialButton>(R.id.btnSaveUrl)
        val btnResetUrl  = findViewById<MaterialButton>(R.id.btnResetUrl)
        val tvConnStatus = findViewById<TextView>(R.id.tvConnectionStatus)
        val progressConn = findViewById<View>(R.id.progressConnection)
        val tvVersion    = findViewById<TextView>(R.id.tvAppVersion)

        // ── SEZIONE QR CODE (solo admin) ──────────────────────────────────────
        val qrSection  = findViewById<View>(R.id.qrSection)        // card + label
        val imgQr      = findViewById<ImageView>(R.id.imgQrCode)   // ImageView nel layout

        if (isAdmin) {
            qrSection.visibility = View.VISIBLE
            imgQr.setImageResource(R.drawable.qr_doormotic)
        } else {
            qrSection.visibility = View.GONE
        }
        // ─────────────────────────────────────────────────────────────────────

        etServerUrl.setText(currentUrl)
        tvVersion.text = "Versione $appVersion"

        // Ripristina URL di default
        btnResetUrl.setOnClickListener {
            etServerUrl.setText(DEFAULT_SERVER_URL)
        }

        // Salva URL
        btnSaveUrl.setOnClickListener {
            val url = etServerUrl.text.toString().trim().trimEnd('/')
            if (url.isEmpty()) {
                etServerUrl.error = "Inserisci un indirizzo valido"
                return@setOnClickListener
            }
            prefs.edit().putString("server_url", url).apply()
            RetrofitClient.resetInstance()
            Toast.makeText(this, "Indirizzo salvato ✅", Toast.LENGTH_SHORT).show()
        }

        // Testa connessione
        btnTestConn.setOnClickListener {
            val url = etServerUrl.text.toString().trim().trimEnd('/')
            if (url.isEmpty()) {
                etServerUrl.error = "Inserisci un indirizzo"
                return@setOnClickListener
            }

            progressConn.visibility = View.VISIBLE
            tvConnStatus.visibility = View.GONE
            btnTestConn.isEnabled   = false

            val prevUrl = prefs.getString("server_url", DEFAULT_SERVER_URL)
            prefs.edit().putString("server_url", url).apply()
            RetrofitClient.resetInstance()

            RetrofitClient.getInstance(this).getStatoPorta()
                .enqueue(object : Callback<StatoPortaResponse> {
                    override fun onResponse(
                        call: Call<StatoPortaResponse>,
                        response: Response<StatoPortaResponse>
                    ) {
                        progressConn.visibility = View.GONE
                        btnTestConn.isEnabled   = true
                        tvConnStatus.visibility = View.VISIBLE
                        if (response.isSuccessful) {
                            tvConnStatus.text = "✅ Connessione riuscita"
                            tvConnStatus.setTextColor(getColor(R.color.access_granted))
                        } else {
                            tvConnStatus.text = "⚠️ Server raggiunto ma risposta inattesa (${response.code()})"
                            tvConnStatus.setTextColor(getColor(R.color.warning))
                            prefs.edit().putString("server_url", prevUrl).apply()
                            RetrofitClient.resetInstance()
                        }
                    }

                    override fun onFailure(call: Call<StatoPortaResponse>, t: Throwable) {
                        progressConn.visibility = View.GONE
                        btnTestConn.isEnabled   = true
                        tvConnStatus.visibility = View.VISIBLE
                        tvConnStatus.text       = "❌ Impossibile raggiungere il server"
                        tvConnStatus.setTextColor(getColor(R.color.error))
                        prefs.edit().putString("server_url", prevUrl).apply()
                        RetrofitClient.resetInstance()
                    }
                })
        }
    }
}