package com.example.progettoappiot

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
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

        settingsBtn.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        registerLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        findViewById<View>(R.id.tvForgotPassword).setOnClickListener {
            showResetPasswordDialog()
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

    // ── Reimposta password (stessa estetica del logout) ───────────────────────
    private fun showResetPasswordDialog() {
        val currentUsername = findViewById<TextInputEditText>(R.id.usernameEditText)
            .text.toString().trim()

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_access, null)

        dialogView.findViewById<TextView>(R.id.tvDialogIcon).text    = "🔑"
        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text   = "Reimposta password"
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text =
            "La richiesta verrà inviata all'amministratore per approvazione."
        dialogView.findViewById<MaterialButton>(R.id.btnConfirm).text = "Invia"
        dialogView.findViewById<MaterialButton>(R.id.btnCancel).text  = "Annulla"

        val dp8  = (8  * resources.displayMetrics.density).toInt()
        val dp16 = (16 * resources.displayMetrics.density).toInt()

        val inputContainer = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp16, dp8, dp16, 0)
        }

        // Replica esatta dello stile OutlinedBox di activity_login.xml:
        // hintEnabled=false, hint inline sull'EditText, bordo viola, sfondo bianco,
        // testo nero, hint grigio, background null, padding 16dp
        fun makeField(hint: String, isPassword: Boolean): TextInputLayout {
            val til = TextInputLayout(
                this,
                null,
                com.google.android.material.R.attr.textInputOutlinedStyle
            ).apply {
                isHintEnabled = false
                boxStrokeColor = ContextCompat.getColor(this@LoginActivity, R.color.lilla_dark)
                setBoxBackgroundColorResource(R.color.white)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dp8, 0, dp8) }
            }

            val et = TextInputEditText(til.context).apply {
                this.hint = hint
                setHintTextColor(
                    ContextCompat.getColorStateList(this@LoginActivity, R.color.gray_medium)
                )
                setTextColor(ContextCompat.getColor(this@LoginActivity, android.R.color.black))
                background = null
                setPadding(dp16, dp16, dp16, dp16)
                if (isPassword) inputType =
                    android.text.InputType.TYPE_CLASS_TEXT or
                            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            }

            til.addView(et)
            return til
        }

        val tilUsername = makeField("Username", false)
        val tilNewPw    = makeField("Nuova password", true)
        val tilConfPw   = makeField("Conferma password", true)

        (tilUsername.editText as? TextInputEditText)?.setText(currentUsername)

        inputContainer.addView(tilUsername)
        inputContainer.addView(tilNewPw)
        inputContainer.addView(tilConfPw)

        val tvMessage = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
        val parent    = tvMessage.parent as android.view.ViewGroup
        parent.addView(inputContainer, parent.indexOfChild(tvMessage) + 1)

        val dialog = AlertDialog.Builder(this, R.style.TransparentDialog)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialogView.findViewById<View>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<View>(R.id.btnConfirm).setOnClickListener {
            val user   = tilUsername.editText?.text.toString().trim()
            val newPw  = tilNewPw.editText?.text.toString()
            val confPw = tilConfPw.editText?.text.toString()

            when {
                user.isEmpty() || newPw.isNullOrEmpty() || confPw.isNullOrEmpty() ->
                    Toast.makeText(this, "Compila tutti i campi", Toast.LENGTH_SHORT).show()
                newPw != confPw ->
                    Toast.makeText(this, "Le password non coincidono", Toast.LENGTH_SHORT).show()
                newPw.length < 6 ->
                    Toast.makeText(this, "Password troppo corta (min 6 caratteri)", Toast.LENGTH_SHORT).show()
                else -> {
                    dialog.dismiss()
                    RetrofitClient.getInstance(this)
                        .requestReset(mapOf("username" to user, "new_password" to newPw))
                        .enqueue(object : Callback<GenericResponse> {
                            override fun onResponse(
                                call: Call<GenericResponse>,
                                response: Response<GenericResponse>
                            ) {
                                val body = response.body()
                                Toast.makeText(
                                    this@LoginActivity,
                                    if (response.isSuccessful && body?.success == true)
                                        "✅ Richiesta inviata! Attendi l'approvazione dell'admin."
                                    else
                                        body?.message ?: "Errore",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            override fun onFailure(call: Call<GenericResponse>, t: Throwable) {
                                Toast.makeText(
                                    this@LoginActivity,
                                    "❌ Server non raggiungibile",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        })
                }
            }
        }

        dialog.show()
    }
}