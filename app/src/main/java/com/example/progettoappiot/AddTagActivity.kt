package com.example.progettoappiot

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Pagina per configurare un tag RFID rilevato dal sistema.
 * Riceve via Intent:
 *   TAG_ID    — UID del tag (es. "A3:FF:12:9C")
 *   TAG_LABEL — label attuale (es. "Sconosciuto")
 *   TAG_ACCESS — has_door_access attuale (Boolean)
 */
class AddTagActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_tag)

        val toolbar = findViewById<Toolbar>(R.id.addTagToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val tagId     = intent.getStringExtra("TAG_ID")     ?: ""
        val tagLabel  = intent.getStringExtra("TAG_LABEL")  ?: ""
        val tagAccess = intent.getBooleanExtra("TAG_ACCESS", false)

        val tvTagId      = findViewById<TextView>(R.id.tvTagId)
        val etLabel      = findViewById<TextInputEditText>(R.id.etTagLabel)
        val switchAccess = findViewById<SwitchMaterial>(R.id.switchTagAccess)
        val btnSave      = findViewById<MaterialButton>(R.id.btnSaveTag)
        val btnDelete    = findViewById<MaterialButton>(R.id.btnDeleteTag)
        val progress     = findViewById<View>(R.id.progressSaveTag)

        tvTagId.text = tagId
        // Precompila con la label attuale solo se non è "Sconosciuto"
        if (tagLabel.isNotEmpty() && tagLabel != "Sconosciuto") {
            etLabel.setText(tagLabel)
        }
        switchAccess.isChecked = tagAccess

        btnSave.setOnClickListener {
            val label = etLabel.text.toString().trim()
            if (label.isEmpty()) {
                etLabel.error = "Inserisci un nome per il tag"
                return@setOnClickListener
            }

            btnSave.isEnabled   = false
            progress.visibility = View.VISIBLE

            val body: Map<String, Any> = mapOf(
                "label"           to label,
                "has_door_access" to switchAccess.isChecked
            )

            RetrofitClient.getInstance(this).updateTag(tagId, body)
                .enqueue(object : Callback<Map<String, Boolean>> {
                    override fun onResponse(
                        call: Call<Map<String, Boolean>>,
                        response: Response<Map<String, Boolean>>
                    ) {
                        progress.visibility = View.GONE
                        btnSave.isEnabled   = true
                        if (response.isSuccessful) {
                            val msg = if (switchAccess.isChecked)
                                "✅ Tag \"$label\" salvato con accesso"
                            else
                                "✅ Tag \"$label\" salvato senza accesso"
                            Toast.makeText(this@AddTagActivity, msg, Toast.LENGTH_SHORT).show()
                            setResult(RESULT_OK)
                            finish()
                        } else {
                            Toast.makeText(
                                this@AddTagActivity,
                                "Errore dal server (${response.code()})",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onFailure(call: Call<Map<String, Boolean>>, t: Throwable) {
                        progress.visibility = View.GONE
                        btnSave.isEnabled   = true
                        Toast.makeText(
                            this@AddTagActivity,
                            "Errore di connessione",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                })
        }

        btnDelete.setOnClickListener {
            showDeleteTagDialog(tagId, tagLabel)
        }
    }

    private fun showDeleteTagDialog(tagId: String, tagLabel: String) {
        val displayName = tagLabel.ifEmpty { tagId }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_access, null)
        dialogView.findViewById<TextView>(R.id.tvDialogIcon).text    = "🗑️"
        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text   = "Elimina tag"
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text =
            "Vuoi eliminare il tag «$displayName»?\nL'operazione non è reversibile."
        dialogView.findViewById<TextView>(R.id.btnConfirm).text = "Sì"
        dialogView.findViewById<TextView>(R.id.btnCancel).text  = "No"

        val dialog = AlertDialog.Builder(this, R.style.TransparentDialog)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialogView.findViewById<View>(R.id.btnCancel).setOnClickListener  { dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btnConfirm).setOnClickListener {
            dialog.dismiss()
            deleteTag(tagId)
        }
        dialog.show()
    }

    private fun deleteTag(tagId: String) {
        val btnDelete = findViewById<MaterialButton>(R.id.btnDeleteTag)
        val btnSave   = findViewById<MaterialButton>(R.id.btnSaveTag)
        val progress  = findViewById<View>(R.id.progressSaveTag)

        btnDelete.isEnabled = false
        btnSave.isEnabled   = false
        progress.visibility = View.VISIBLE

        RetrofitClient.getInstance(this).deleteTag(tagId)
            .enqueue(object : Callback<GenericResponse> {
                override fun onResponse(call: Call<GenericResponse>, response: Response<GenericResponse>) {
                    progress.visibility = View.GONE
                    btnDelete.isEnabled = true
                    btnSave.isEnabled   = true
                    if (response.isSuccessful) {
                        Toast.makeText(this@AddTagActivity, "🗑️ Tag eliminato", Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        Toast.makeText(this@AddTagActivity, "Errore dal server (${response.code()})", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<GenericResponse>, t: Throwable) {
                    progress.visibility = View.GONE
                    btnDelete.isEnabled = true
                    btnSave.isEnabled   = true
                    Toast.makeText(this@AddTagActivity, "Errore di connessione", Toast.LENGTH_SHORT).show()
                }
            })
    }
}