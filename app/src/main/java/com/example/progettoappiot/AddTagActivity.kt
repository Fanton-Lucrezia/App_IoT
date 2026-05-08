package com.example.progettoappiot

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
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

        val tvTagId   = findViewById<TextView>(R.id.tvTagId)
        val etLabel   = findViewById<TextInputEditText>(R.id.etTagLabel)
        val switchAccess = findViewById<SwitchMaterial>(R.id.switchTagAccess)
        val btnSave   = findViewById<MaterialButton>(R.id.btnSaveTag)
        val progress  = findViewById<View>(R.id.progressSaveTag)

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

            btnSave.isEnabled  = false
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
    }
}
