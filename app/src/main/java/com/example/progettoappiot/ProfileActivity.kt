package com.example.progettoappiot

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.io.InputStream

class ProfileActivity : AppCompatActivity() {

    private lateinit var ivAvatar: ImageView
    private lateinit var tvUsername: TextView
    private lateinit var tvRole: TextView
    private lateinit var username: String
    private lateinit var progressBar: View

    // Launcher per selezionare immagine dalla galleria
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> uploadProfilePicture(uri) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val toolbar = findViewById<Toolbar>(R.id.profileToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        ivAvatar    = findViewById(R.id.ivProfileAvatar)
        tvUsername  = findViewById(R.id.tvProfileUsername)
        tvRole      = findViewById(R.id.tvProfileRole)
        progressBar = findViewById(R.id.progressProfile)

        val prefs   = getSharedPreferences("DOORmotic", MODE_PRIVATE)
        username    = prefs.getString("username", "") ?: ""
        val isAdmin = prefs.getBoolean("is_admin", false)

        tvUsername.text = username
        tvRole.text     = if (isAdmin) "Amministratore" else "Utente"

        // Carica foto profilo salvata localmente (cache rapida)
        val savedPic = prefs.getString("profile_picture_b64", null)
        if (!savedPic.isNullOrEmpty()) {
            setAvatarFromBase64(savedPic)
        }

        // Tap sull'avatar → scegli foto
        ivAvatar.setOnClickListener { openGallery() }
        findViewById<View>(R.id.btnChangePicture).setOnClickListener { openGallery() }

        // Cambia password
        findViewById<MaterialButton>(R.id.btnChangePassword).setOnClickListener {
            showChangePasswordDialog()
        }

        // Logout
        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Esci")
                .setMessage("Vuoi davvero uscire dall'account?")
                .setPositiveButton("Esci") { _, _ -> logout() }
                .setNegativeButton("Annulla", null)
                .show()
        }
    }

    // ── Foto profilo ──────────────────────────────────────────────────────────

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
        pickImageLauncher.launch(intent)
    }

    private fun uploadProfilePicture(uri: Uri) {
        val inputStream: InputStream = contentResolver.openInputStream(uri) ?: return
        val bitmap = BitmapFactory.decodeStream(inputStream)

        // Ridimensiona a max 256×256 per limitare la dimensione in DB
        val scaled = Bitmap.createScaledBitmap(bitmap, 256, 256, true)
        val baos   = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, baos)
        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

        progressBar.visibility = View.VISIBLE

        RetrofitClient.getInstance(this)
            .updateProfilePicture(username, mapOf("profile_picture" to b64))
            .enqueue(object : Callback<GenericResponse> {
                override fun onResponse(call: Call<GenericResponse>, response: Response<GenericResponse>) {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful) {
                        setAvatarFromBase64(b64)
                        // Salva anche in locale per caricamento rapido
                        getSharedPreferences("DOORmotic", MODE_PRIVATE)
                            .edit().putString("profile_picture_b64", b64).apply()
                        Toast.makeText(this@ProfileActivity, "Foto aggiornata", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@ProfileActivity, "Errore caricamento foto", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<GenericResponse>, t: Throwable) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@ProfileActivity, "Errore di connessione", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun setAvatarFromBase64(b64: String) {
        try {
            val bytes  = Base64.decode(b64, Base64.NO_WRAP)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ivAvatar.setImageBitmap(bitmap)
        } catch (_: Exception) { }
    }

    // ── Cambio password ───────────────────────────────────────────────────────

    private fun showChangePasswordDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_change_password, null)
        val etOld = view.findViewById<TextInputEditText>(R.id.etOldPassword)
        val etNew = view.findViewById<TextInputEditText>(R.id.etNewPassword)
        val etConf= view.findViewById<TextInputEditText>(R.id.etConfirmPassword)

        MaterialAlertDialogBuilder(this)
            .setTitle("Cambia password")
            .setView(view)
            .setPositiveButton("Salva", null) // override sotto per non chiudere in caso di errore
            .setNegativeButton("Annulla", null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val oldPw  = etOld.text.toString()
                        val newPw  = etNew.text.toString()
                        val confPw = etConf.text.toString()

                        if (oldPw.isEmpty() || newPw.isEmpty() || confPw.isEmpty()) {
                            Toast.makeText(this, "Compila tutti i campi", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        if (newPw != confPw) {
                            etConf.error = "Le password non coincidono"
                            return@setOnClickListener
                        }
                        if (newPw.length < 6) {
                            etNew.error = "Minimo 6 caratteri"
                            return@setOnClickListener
                        }

                        doChangePassword(oldPw, newPw, dialog)
                    }
                }
                dialog.show()
            }
    }

    private fun doChangePassword(oldPw: String, newPw: String, dialog: AlertDialog) {
        RetrofitClient.getInstance(this)
            .changePassword(username, mapOf("old_password" to oldPw, "new_password" to newPw))
            .enqueue(object : Callback<GenericResponse> {
                override fun onResponse(call: Call<GenericResponse>, response: Response<GenericResponse>) {
                    val body = response.body()
                    if (response.isSuccessful && body?.success == true) {
                        Toast.makeText(this@ProfileActivity, "Password aggiornata ✅", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    } else {
                        Toast.makeText(this@ProfileActivity, body?.message ?: "Errore", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<GenericResponse>, t: Throwable) {
                    Toast.makeText(this@ProfileActivity, "Errore di connessione", Toast.LENGTH_SHORT).show()
                }
            })
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    private fun logout() {
        getSharedPreferences("DOORmotic", MODE_PRIVATE).edit().clear().apply()
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }
}
