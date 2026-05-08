package com.example.progettoappiot

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class RegisterActivity : AppCompatActivity() {

    // ── Launcher per il nostro scanner quadrato ──────────────────────────
    private val scanLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanned = result.data?.getStringExtra("SCAN_RESULT")
            if (!scanned.isNullOrEmpty()) {
                findViewById<TextInputEditText>(R.id.regSecretCode).setText(scanned)
                Toast.makeText(this, "✓ Codice acquisito dal QR", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val usernameET     = findViewById<TextInputEditText>(R.id.regUsername)
        val passwordET     = findViewById<TextInputEditText>(R.id.regPassword)
        val passwordConfET = findViewById<TextInputEditText>(R.id.regPasswordConfirm)
        val secretCodeET   = findViewById<TextInputEditText>(R.id.regSecretCode)
        val btnScanQR      = findViewById<View>(R.id.btnScanQR)
        val btnRegister    = findViewById<MaterialButton>(R.id.btnRegister)
        val tvBack         = findViewById<View>(R.id.tvBackToLogin)
        val loadingView    = findViewById<View>(R.id.loadingOverlay)

        // ── Apri scanner quadrato ────────────────────────────────────────
        btnScanQR.setOnClickListener {
            scanLauncher.launch(Intent(this, CustomScanActivity::class.java))
        }

        // ── Registrazione ────────────────────────────────────────────────
        btnRegister.setOnClickListener {
            val user    = usernameET.text.toString().trim()
            val pass    = passwordET.text.toString()
            val confirm = passwordConfET.text.toString()
            val code    = secretCodeET.text.toString().trim()

            when {
                user.isEmpty() -> {
                    Toast.makeText(this, "Inserisci un username", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                pass.isEmpty() -> {
                    Toast.makeText(this, "Inserisci una password", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                pass.length < 6 -> {
                    Toast.makeText(this, "Password troppo corta (min 6 caratteri)", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                pass != confirm -> {
                    Toast.makeText(this, "❌ Le password non coincidono", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            loadingView.visibility = View.VISIBLE
            btnRegister.isEnabled = false

            RetrofitClient.getInstance(this)
                .register(mapOf("username" to user, "password" to pass, "secret_code" to code))
                .enqueue(object : Callback<RegisterResponse> {

                    override fun onResponse(
                        call: Call<RegisterResponse>,
                        response: Response<RegisterResponse>
                    ) {
                        loadingView.visibility = View.GONE
                        btnRegister.isEnabled = true

                        val body = response.body()
                        if (response.isSuccessful && body?.success == true) {
                            Toast.makeText(this@RegisterActivity, body.message, Toast.LENGTH_LONG).show()
                            finish()
                        } else {
                            Toast.makeText(
                                this@RegisterActivity,
                                body?.message ?: "Errore durante la registrazione",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onFailure(call: Call<RegisterResponse>, t: Throwable) {
                        loadingView.visibility = View.GONE
                        btnRegister.isEnabled = true
                        Toast.makeText(this@RegisterActivity, "Impossibile connettersi al server", Toast.LENGTH_SHORT).show()
                    }
                })
        }

        tvBack.setOnClickListener { finish() }
    }
}
