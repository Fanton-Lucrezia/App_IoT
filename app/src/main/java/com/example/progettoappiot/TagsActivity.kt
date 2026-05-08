package com.example.progettoappiot

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TagsActivity : AppCompatActivity() {

    companion object {
        const val REQUEST_CONFIGURE_TAG = 1001
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: View
    private lateinit var tvEmpty: View
    private lateinit var tvInfo: TextView
    private val tagList = mutableListOf<Tag>()
    private lateinit var adapter: TagAdapter

    // Launcher per tornare da AddTagActivity e ricaricare
    private val configureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            loadTags()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tags)

        val toolbar = findViewById<Toolbar>(R.id.tagsToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        recyclerView = findViewById(R.id.rvTags)
        progressBar  = findViewById(R.id.progressTags)
        tvEmpty      = findViewById(R.id.tvTagsEmpty)
        tvInfo       = findViewById(R.id.tvTagsInfo)

        adapter = TagAdapter(tagList) { tag ->
            openConfigureTag(tag)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        loadTags()
    }

    override fun onResume() {
        super.onResume()
        loadTags()
    }

    private fun loadTags() {
        progressBar.visibility  = View.VISIBLE
        recyclerView.visibility = View.GONE
        tvEmpty.visibility      = View.GONE
        tvInfo.visibility       = View.GONE

        RetrofitClient.getInstance(this).getTags()
            .enqueue(object : Callback<List<Tag>> {
                override fun onResponse(call: Call<List<Tag>>, response: Response<List<Tag>>) {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful) {
                        val tags = response.body() ?: emptyList()
                        tagList.clear()
                        // Metti i tag "Sconosciuto" in cima
                        tagList.addAll(tags.sortedWith(
                            compareByDescending<Tag> { it.label.isNullOrBlank() || it.label == "Sconosciuto" }
                                .thenBy { it.label }
                        ))
                        adapter.notifyDataSetChanged()

                        val unknownCount = tags.count { it.label.isNullOrBlank() || it.label == "Sconosciuto" }
                        tvInfo.visibility = View.VISIBLE
                        tvInfo.text = when {
                            unknownCount > 0 -> "⚠️ $unknownCount tag da configurare · ${tags.size} totali"
                            tags.isNotEmpty() -> "${tags.size} tag registrati"
                            else -> ""
                        }

                        if (tags.isEmpty()) {
                            tvEmpty.visibility = View.VISIBLE
                        } else {
                            recyclerView.visibility = View.VISIBLE
                        }
                    } else {
                        Toast.makeText(this@TagsActivity, "Errore caricamento tag", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<List<Tag>>, t: Throwable) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@TagsActivity, "Errore di connessione", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun openConfigureTag(tag: Tag) {
        val intent = Intent(this, AddTagActivity::class.java).apply {
            putExtra("TAG_ID",     tag.tag_id     ?: "")
            putExtra("TAG_LABEL",  tag.label       ?: "")
            putExtra("TAG_ACCESS", tag.has_door_access ?: false)
        }
        configureLauncher.launch(intent)
    }
}

// ── Adapter ───────────────────────────────────────────────────────────────────

class TagAdapter(
    private val tags: List<Tag>,
    private val onEdit: (Tag) -> Unit
) : RecyclerView.Adapter<TagAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvInitial: TextView = view.findViewById(R.id.tvTagInitial)
        val tvLabel: TextView   = view.findViewById(R.id.tvTagLabel)
        val tvTagId: TextView   = view.findViewById(R.id.tvTagId)
        val chipAccess: Chip    = view.findViewById(R.id.chipTagAccess)
        val chipNew: Chip       = view.findViewById(R.id.chipTagNew)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tag, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tag       = tags[position]
        val isUnknown = tag.label.isNullOrBlank() || tag.label == "Sconosciuto"
        val label     = if (isUnknown) "Sconosciuto" else tag.label ?: "—"

        holder.tvLabel.text  = label
        holder.tvTagId.text  = tag.tag_id ?: "—"
        holder.tvInitial.text = if (isUnknown) "?" else label.first().uppercaseChar().toString()

        // Chip "badge nuovo" — visibile solo se sconosciuto
        holder.chipNew.visibility = if (isUnknown) View.VISIBLE else View.GONE

        // Chip accesso
        val hasAccess = tag.has_door_access ?: false
        holder.chipAccess.text = if (hasAccess) "Accesso" else "Negato"
        holder.chipAccess.setChipBackgroundColorResource(
            if (hasAccess) R.color.chip_access_bg else R.color.chip_denied_bg
        )
        holder.chipAccess.setTextColor(
            holder.itemView.context.getColor(
                if (hasAccess) R.color.chip_access_text else R.color.chip_denied_text
            )
        )

        // Colore iniziale
        holder.tvInitial.setBackgroundResource(
            if (isUnknown) R.drawable.bg_avatar_warning else R.drawable.bg_avatar
        )

        holder.itemView.setOnClickListener { onEdit(tag) }
    }

    override fun getItemCount() = tags.size
}
