package com.example.progettoappiot

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout:    DrawerLayout
    private lateinit var statusEmoji:     TextView
    private lateinit var statusTextView:  TextView
    private lateinit var btnDoor:         MaterialButton
    private lateinit var adapter:         AccessoAdapter
    private lateinit var ivDarkModeToggle: ImageView

    private var isAdmin       = false
    private var hasDoorAccess = false
    private var username      = ""
    private var isPortaAperta = false
    private var ignoraPolling  = false

    private val handler = Handler(Looper.getMainLooper())
    private val pollingRunnable = object : Runnable {
        override fun run() {
            fetchStatoPorta()
            if (isAdmin) {
                fetchAccessi()
                checkUnknownTags()
            }
            handler.postDelayed(this, 3_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Ripristina modalità dark/light salvata
        val prefs = getSharedPreferences("DOORmotic", MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        isAdmin       = intent.getBooleanExtra("IS_ADMIN",       prefs.getBoolean("is_admin", false))
        hasDoorAccess = intent.getBooleanExtra("HAS_DOOR_ACCESS", prefs.getBoolean("has_door_access", false))
        username      = intent.getStringExtra("USERNAME")         ?: prefs.getString("username", "Utente") ?: "Utente"

        setupToolbar()
        setupDrawer()
        setupDoorCard()
        setupAdminSection()

        if (isAdmin) scheduleBackgroundPolling()
        handler.post(pollingRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(pollingRunnable)
    }

    // ── Toolbar con toggle dark mode ──────────────────────────────────
    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        drawerLayout = findViewById(R.id.drawer_layout)

        ivDarkModeToggle = findViewById(R.id.ivDarkModeToggle)
        val prefs = getSharedPreferences("DOORmotic", MODE_PRIVATE)
        updateDarkModeIcon(prefs.getBoolean("dark_mode", false))

        ivDarkModeToggle.setOnClickListener {
            val currentlyDark = prefs.getBoolean("dark_mode", false)
            val newDark = !currentlyDark
            prefs.edit().putBoolean("dark_mode", newDark).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (newDark) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
            // recreate() viene chiamato automaticamente da AppCompatDelegate
        }

        findViewById<ImageView>(R.id.menu_icon).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.END)
        }
    }

    private fun updateDarkModeIcon(isDark: Boolean) {
        ivDarkModeToggle.setImageResource(
            if (isDark) R.drawable.ic_sun else R.drawable.ic_moon
        )
    }

    // ── Drawer ────────────────────────────────────────────────────────
    private fun setupDrawer() {
        val navView = findViewById<NavigationView>(R.id.nav_view)
        val header  = navView.getHeaderView(0)
        header.findViewById<TextView>(R.id.navHeaderUsername)?.text = username
        header.findViewById<TextView>(R.id.navHeaderRole)?.text =
            if (isAdmin) "Amministratore" else "Utente"

        // Carica foto profilo nel drawer header se presente
        val prefs   = getSharedPreferences("DOORmotic", MODE_PRIVATE)
        val picB64  = prefs.getString("profile_picture_b64", null)
        val ivHeaderAvatar = header.findViewById<ImageView>(R.id.navHeaderAvatar)
        if (!picB64.isNullOrEmpty() && ivHeaderAvatar != null) {
            try {
                val bytes  = android.util.Base64.decode(picB64, android.util.Base64.NO_WRAP)
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ivHeaderAvatar.setImageBitmap(bitmap)
            } catch (_: Exception) { }
        }

        // Mostra "Storico", "Tag RFID" e "Gestione Utenti" solo all'admin
        navView.menu.findItem(R.id.nav_storico)?.isVisible       = isAdmin
        navView.menu.findItem(R.id.nav_tags)?.isVisible          = isAdmin
        navView.menu.findItem(R.id.nav_users)?.isVisible         = isAdmin

        navView.setNavigationItemSelectedListener { item ->
            drawerLayout.closeDrawer(GravityCompat.END)
            when (item.itemId) {
                R.id.nav_storico  -> startActivity(Intent(this, StoricoActivity::class.java))
                R.id.nav_tags     -> startActivity(Intent(this, TagsActivity::class.java))
                R.id.nav_users    -> startActivity(Intent(this, UsersActivity::class.java))
                R.id.nav_profile  -> startActivity(
                    Intent(this, ProfileActivity::class.java).apply {
                        putExtra("IS_ADMIN", isAdmin)
                        putExtra("USERNAME", username)
                    }
                )
                R.id.nav_settings -> startActivity(Intent(this, SettingsActivity::class.java))
                R.id.nav_logout   -> showLogoutDialog()
            }
            true
        }
    }

    // ── Card porta ────────────────────────────────────────────────────
    private fun setupDoorCard() {
        statusEmoji    = findViewById(R.id.statusEmoji)
        statusTextView = findViewById(R.id.statusTextView)
        btnDoor        = findViewById(R.id.btnApriPorta)

        if (!hasDoorAccess) {
            btnDoor.visibility = View.GONE
            findViewById<TextView>(R.id.noAccessText).visibility = View.VISIBLE
        }

        btnDoor.setOnClickListener {
            btnDoor.isEnabled = false
            if (isPortaAperta) chiudiPorta() else apriPorta()
        }
    }

    // ── Sezione admin: log + banner tag sconosciuto ────────────────────
    private fun setupAdminSection() {
        val adminSection = findViewById<View>(R.id.adminLogSection)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        val refreshFab   = findViewById<FloatingActionButton>(R.id.refreshFab)
        val tvVediTutti  = findViewById<TextView>(R.id.tvVediTutti)
        val bannerUnknown = findViewById<View>(R.id.bannerUnknownTag)

        if (isAdmin) {
            adminSection.visibility = View.VISIBLE
            refreshFab.visibility   = View.VISIBLE

            adapter = AccessoAdapter(emptyList())
            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter       = adapter

            tvVediTutti.setOnClickListener {
                startActivity(Intent(this, StoricoActivity::class.java))
            }

            refreshFab.setOnClickListener { fetchAccessi() }

            // Tap sul banner → apre la lista tag per configurarli
            bannerUnknown?.setOnClickListener {
                startActivity(Intent(this, TagsActivity::class.java))
            }
        }
    }

    // ── Controllo tag sconosciuti (label = "Sconosciuto") ──────────────
    private fun checkUnknownTags() {
        RetrofitClient.getInstance(this).getTags()
            .enqueue(object : Callback<List<Tag>> {
                override fun onResponse(call: Call<List<Tag>>, response: Response<List<Tag>>) {
                    if (response.isSuccessful) {
                        val unknown = response.body()
                            ?.count { it.label.isNullOrBlank() || it.label == "Sconosciuto" }
                            ?: 0
                        val banner     = findViewById<View>(R.id.bannerUnknownTag) ?: return
                        val tvBannerCount = banner.findViewById<TextView>(R.id.tvBannerCount)
                        if (unknown > 0) {
                            banner.visibility = View.VISIBLE
                            tvBannerCount?.text =
                                if (unknown == 1) "1 nuovo tag da configurare"
                                else "$unknown nuovi tag da configurare"
                        } else {
                            banner.visibility = View.GONE
                        }
                    }
                }
                override fun onFailure(call: Call<List<Tag>>, t: Throwable) {}
            })
    }

    // ── API porta ─────────────────────────────────────────────────────
    private fun apriPorta() {
        RetrofitClient.getInstance(this).apriPorta(mapOf("username" to username))
            .enqueue(object : Callback<Void> {
                override fun onResponse(call: Call<Void>, response: Response<Void>) {
                    btnDoor.isEnabled = true
                    if (response.isSuccessful) {
                        isPortaAperta = true
                        ignoraPolling = true
                        updateDoorUI(true)
                        if (isAdmin) fetchAccessi()
                        Toast.makeText(this@MainActivity, "✓ Porta aperta", Toast.LENGTH_SHORT).show()
                        handler.postDelayed({ ignoraPolling = false }, 5000L)
                    }
                }
                override fun onFailure(call: Call<Void>, t: Throwable) {
                    btnDoor.isEnabled = true
                    Toast.makeText(this@MainActivity, "Errore connessione", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun chiudiPorta() {
        RetrofitClient.getInstance(this).chiudiPorta(mapOf("username" to username))
            .enqueue(object : Callback<Void> {
                override fun onResponse(call: Call<Void>, response: Response<Void>) {
                    btnDoor.isEnabled = true
                    if (response.isSuccessful) {
                        isPortaAperta = false
                        ignoraPolling = true
                        updateDoorUI(false)
                        if (isAdmin) fetchAccessi()
                        Toast.makeText(this@MainActivity, "✓ Porta bloccata", Toast.LENGTH_SHORT).show()
                        handler.postDelayed({ ignoraPolling = false }, 5000L)
                    }
                }
                override fun onFailure(call: Call<Void>, t: Throwable) {
                    btnDoor.isEnabled = true
                    Toast.makeText(this@MainActivity, "Errore connessione", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun fetchStatoPorta() {
        RetrofitClient.getInstance(this).getStatoPorta()
            .enqueue(object : Callback<StatoPortaResponse> {
                override fun onResponse(call: Call<StatoPortaResponse>, response: Response<StatoPortaResponse>) {
                    if (response.isSuccessful && !ignoraPolling) {
                        val stato = response.body()?.stato ?: "Bloccata"
                        isPortaAperta = stato.lowercase() == "aperta"
                        updateDoorUI(isPortaAperta)
                    }
                }
                override fun onFailure(call: Call<StatoPortaResponse>, t: Throwable) {}
            })
    }

    private fun fetchAccessi() {
        if (!isAdmin) return
        RetrofitClient.getInstance(this).getAccessi(limit = 5)
            .enqueue(object : Callback<List<Accesso>> {
                override fun onResponse(call: Call<List<Accesso>>, response: Response<List<Accesso>>) {
                    if (response.isSuccessful) {
                        adapter.updateData(response.body() ?: emptyList())
                    }
                }
                override fun onFailure(call: Call<List<Accesso>>, t: Throwable) {
                    Log.w("DOORmotic", "fetchAccessi: ${t.message}")
                }
            })
    }

    // ── UI porta ──────────────────────────────────────────────────────
    private fun updateDoorUI(aperta: Boolean) {
        if (aperta) {
            statusEmoji.text = "🔓"
            statusTextView.text = "APERTA"
            statusTextView.setTextColor(ContextCompat.getColor(this, R.color.door_open))
            if (hasDoorAccess) btnDoor.text = "BLOCCA PORTA"
        } else {
            statusEmoji.text = "🔒"
            statusTextView.text = "BLOCCATA"
            statusTextView.setTextColor(ContextCompat.getColor(this, R.color.door_closed))
            if (hasDoorAccess) btnDoor.text = "APRI PORTA"
        }
    }

    // ── WorkManager ───────────────────────────────────────────────────
    private fun scheduleBackgroundPolling() {
        try {
            val req = PeriodicWorkRequestBuilder<DoorPollingWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "door_polling", ExistingPeriodicWorkPolicy.KEEP, req
            )
        } catch (e: Exception) {
            Log.w("DOORmotic", "WorkManager: ${e.message}")
        }
    }

    // ── Logout ────────────────────────────────────────────────────────
    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Sei sicuro di voler uscire?")
            .setPositiveButton("Sì") { _, _ ->
                handler.removeCallbacks(pollingRunnable)
                WorkManager.getInstance(this).cancelUniqueWork("door_polling")
                getSharedPreferences("DOORmotic", MODE_PRIVATE).edit().clear().apply()
                RetrofitClient.resetInstance()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .setNegativeButton("No", null)
            .show()
    }
}
