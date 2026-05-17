package com.example.progettoappiot

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
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

        val savedPic = prefs.getString("profile_picture_b64", null)
        if (!savedPic.isNullOrEmpty()) setAvatarFromBase64(savedPic)

        ivAvatar.setOnClickListener { openGallery() }
        findViewById<View>(R.id.btnChangePicture).setOnClickListener { openGallery() }

        findViewById<MaterialButton>(R.id.btnChangePassword).setOnClickListener {
            showChangePasswordDialog()
        }

        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
            showLogoutDialog()
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

        // Ritaglia al centro un quadrato perfetto per evitare la deformazione
        val size = minOf(bitmap.width, bitmap.height)
        val xOffset = (bitmap.width - size) / 2
        val yOffset = (bitmap.height - size) / 2
        val cropped = Bitmap.createBitmap(bitmap, xOffset, yOffset, size, size)

        val scaled = Bitmap.createScaledBitmap(cropped, 256, 256, true)
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
            ivAvatar.setPadding(0, 0, 0, 0)
            ivAvatar.imageTintList = null
            ivAvatar.background = null          // rimuove lo sfondo lilla che causa la "ciambella"
            ivAvatar.setImageBitmap(bitmap)
        } catch (_: Exception) { }
    }

    // ── Cambio password ───────────────────────────────────────────────────────
    // Usa Dialog trasparente con layout custom (dialog_change_password.xml)
    // così rispetta la card con angoli arrotondati, icona e bottoni stilizzati.

    private fun showChangePasswordDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_change_password)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.setCancelable(true)

        val etOld  = dialog.findViewById<TextInputEditText>(R.id.etOldPassword)
        val etNew  = dialog.findViewById<TextInputEditText>(R.id.etNewPassword)
        val etConf = dialog.findViewById<TextInputEditText>(R.id.etConfirmPassword)

        dialog.findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.findViewById<MaterialButton>(R.id.btnConfirm).setOnClickListener {
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

        dialog.show()
    }

    private fun doChangePassword(oldPw: String, newPw: String, dialog: Dialog) {
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

    private fun showLogoutDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_access, null)

        dialogView.findViewById<TextView>(R.id.tvDialogIcon).text    = "👋"
        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text   = "Logout"
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text  = "Sei sicuro di voler uscire?"
        dialogView.findViewById<MaterialButton>(R.id.btnConfirm).text = "Sì"
        dialogView.findViewById<MaterialButton>(R.id.btnCancel).text  = "No"

        val dialog = AlertDialog.Builder(this, R.style.TransparentDialog)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialogView.findViewById<View>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.btnConfirm).setOnClickListener {
            dialog.dismiss()
            getSharedPreferences("DOORmotic", MODE_PRIVATE).edit().clear().apply()
            startActivity(
                Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
        }

        dialog.show()
    }
}