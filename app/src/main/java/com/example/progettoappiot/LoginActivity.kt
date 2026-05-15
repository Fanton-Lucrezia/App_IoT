package com.example.progettoappiot

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.messaging.FirebaseMessaging
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val usernameET   = findViewById<TextInputEditText>(R.id.usernameEditText)
        val passwordET   = findViewById<TextInputEditText>(R.id.passwordEditText)
        val loginBtn     = findViewById<MaterialButton>(R.id.loginButton)
        val registerLink = findViewById<View>(R.id.registerTextView)
        val settingsBtn  = findViewById<View>(R.id.btnSettings)
        val loadingBar   = findViewById<View>(R.id.loadingOverlay)

        val prefs     = getSharedPreferences("DOORmotic", MODE_PRIVATE)
        val savedUser = prefs.getString("saved_username", null)
        val savedPass = prefs.getString("saved_password", null)

        // ── SUGGERIMENTO CREDENZIALI al tocco del campo username ──────────────
        // Se esistono credenziali salvate, al primo focus appare il dialog
        if (!savedUser.isNullOrEmpty() && !savedPass.isNullOrEmpty()) {
            usernameET.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    showCredentialDialog(
                        savedUser, savedPass,
                        usernameET, passwordET,
                        loadingBar, loginBtn, prefs
                    )
                }
            }
        }
        // ─────────────────────────────────────────────────────────────────────

        settingsBtn.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        registerLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        loginBtn.setOnClickListener {
            val user = usernameET.text.toString().trim()
            val pass = passwordET.text.toString()

            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Inserisci username e password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            loadingBar.visibility = View.VISIBLE
            loginBtn.isEnabled    = false
            performLogin(user, pass, loadingBar, loginBtn, prefs)
        }
    }

    // ── Dialog "Vuoi accedere come X?" ───────────────────────────────────────
    private fun showCredentialDialog(
        savedUser: String,
        savedPass: String,
        usernameET: TextInputEditText,
        passwordET: TextInputEditText,
        loadingBar: View,
        loginBtn: View,
        prefs: android.content.SharedPreferences
    ) {
        AlertDialog.Builder(this)
            .setTitle("Accesso rapido")
            .setMessage("Vuoi accedere come $savedUser?")
            .setPositiveButton("Sì, accedi") { _, _ ->
                usernameET.setText(savedUser)
                passwordET.setText(savedPass)
                usernameET.clearFocus()
                loadingBar.visibility = View.VISIBLE
                loginBtn.isEnabled    = false
                performLogin(savedUser, savedPass, loadingBar, loginBtn, prefs)
            }
            .setNegativeButton("No, inserisci manualmente") { dialog, _ ->
                usernameET.setText("")
                passwordET.setText("")
                usernameET.clearFocus()
                dialog.dismiss()
            }
            .setNeutralButton("Dimentica account") { dialog, _ ->
                prefs.edit()
                    .remove("saved_username")
                    .remove("saved_password")
                    .apply()
                usernameET.onFocusChangeListener = null
                usernameET.clearFocus()
                dialog.dismiss()
                Toast.makeText(this, "Credenziali dimenticate", Toast.LENGTH_SHORT).show()
            }
            .setCancelable(true)
            .show()
    }

    // ── Logica di login ───────────────────────────────────────────────────────
    private fun performLogin(
        user: String,
        pass: String,
        loadingBar: View,
        loginBtn: View,
        prefs: android.content.SharedPreferences
    ) {
        RetrofitClient.getInstance(this)
            .login(mapOf("username" to user, "password" to pass))
            .enqueue(object : Callback<LoginResponse> {

                override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                    loadingBar.visibility = View.GONE
                    loginBtn.isEnabled    = true

                    val body = response.body()
                    if (response.isSuccessful && body?.success == true) {
                        val uname   = body.username ?: user
                        val isAdmin = body.is_admin ?: false
                        val hasDoor = body.has_door_access ?: false

                        // Salva sessione + credenziali per il prossimo suggerimento
                        prefs.edit()
                            .putString("username",         uname)
                            .putBoolean("is_admin",        isAdmin)
                            .putBoolean("has_door_access", hasDoor)
                            .putString("saved_username",   user)
                            .putString("saved_password",   pass)
                            .apply()

                        if (isAdmin) {
                            FirebaseMessaging.getInstance().token.addOnSuccessListener { fcmToken ->
                                RetrofitClient.getInstance(this@LoginActivity)
                                    .registerFcmToken(mapOf(
                                        "username"  to uname,
                                        "fcm_token" to fcmToken
                                    ))
                                    .enqueue(object : Callback<Map<String, Boolean>> {
                                        override fun onResponse(c: Call<Map<String, Boolean>>, r: Response<Map<String, Boolean>>) {
                                            android.util.Log.d("FCM", "Token registrato su Flask")
                                        }
                                        override fun onFailure(c: Call<Map<String, Boolean>>, t: Throwable) {
                                            android.util.Log.w("FCM", "Errore registrazione token: ${t.message}")
                                        }
                                    })
                            }
                        }

                        startActivity(
                            Intent(this@LoginActivity, MainActivity::class.java).apply {
                                putExtra("USERNAME",        uname)
                                putExtra("IS_ADMIN",        isAdmin)
                                putExtra("HAS_DOOR_ACCESS", hasDoor)
                            }
                        )
                        finish()

                    } else {
                        Toast.makeText(
                            this@LoginActivity,
                            body?.message ?: "Credenziali non valide",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                    loadingBar.visibility = View.GONE
                    loginBtn.isEnabled    = true
                    Toast.makeText(
                        this@LoginActivity,
                        "❌ Server non raggiungibile.\nControlla l'IP in ⚙ Configura server",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
    }
}