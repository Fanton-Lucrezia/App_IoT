package com.example.progettoappiot

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class UsersActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: View
    private lateinit var emptyState: View
    private lateinit var tvUserCount: TextView
    private lateinit var etSearch: TextInputEditText
    private lateinit var adapter: UserAdapter

    private val allUsers = mutableListOf<UserItem>()
    private val filteredUsers = mutableListOf<UserItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_users)

        val toolbar = findViewById<Toolbar>(R.id.usersToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        recyclerView  = findViewById(R.id.rvUsers)
        progressBar   = findViewById(R.id.progressUsers)
        emptyState    = findViewById(R.id.emptyStateUsers)
        tvUserCount   = findViewById(R.id.tvUserCount)
        etSearch      = findViewById(R.id.etSearchUsers)

        adapter = UserAdapter(filteredUsers,
            onToggleAccess = { user, hasAccess -> confirmToggleAccess(user, hasAccess) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Ricerca live
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { filterUsers(s.toString()) }
            override fun afterTextChanged(s: Editable?) {}
        })

        findViewById<View>(R.id.fabRefreshUsers).setOnClickListener { loadUsers() }

        loadUsers()
    }

    override fun onResume() {
        super.onResume()
        loadUsers()
    }

    private fun loadUsers() {
        progressBar.visibility  = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyState.visibility   = View.GONE

        RetrofitClient.getInstance(this).getUsers()
            .enqueue(object : Callback<List<UserItem>> {
                override fun onResponse(call: Call<List<UserItem>>, response: Response<List<UserItem>>) {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful) {
                        val users = response.body() ?: emptyList()
                        allUsers.clear()
                        // Admin in cima, poi alfabetico
                        allUsers.addAll(users.sortedWith(
                            compareByDescending<UserItem> { it.is_admin ?: false }
                                .thenBy { it.username }
                        ))
                        filterUsers(etSearch.text.toString())
                        updateUserCount()
                    } else {
                        Toast.makeText(this@UsersActivity, "Errore caricamento utenti", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<List<UserItem>>, t: Throwable) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@UsersActivity, "Errore di connessione", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun filterUsers(query: String) {
        filteredUsers.clear()
        filteredUsers.addAll(
            if (query.isBlank()) allUsers
            else allUsers.filter { it.username.contains(query.trim(), ignoreCase = true) }
        )
        adapter.notifyDataSetChanged()

        val hasResults = filteredUsers.isNotEmpty()
        recyclerView.visibility = if (hasResults) View.VISIBLE else View.GONE
        emptyState.visibility   = if (hasResults) View.GONE else View.VISIBLE
    }

    private fun updateUserCount() {
        val total  = allUsers.size
        val active = allUsers.count { it.has_door_access }
        tvUserCount.text = "$total utenti · $active con accesso"
    }

    private fun confirmToggleAccess(user: UserItem, hasAccess: Boolean) {
        val action = if (hasAccess) "concedere" else "revocare"
        val icon   = if (hasAccess) "✅" else "🚫"
        AlertDialog.Builder(this)
            .setTitle("$icon Modifica accesso")
            .setMessage("Vuoi $action l'accesso alla porta a «${user.username}»?")
            .setPositiveButton("Conferma") { _, _ -> toggleUserAccess(user.username, hasAccess) }
            .setNegativeButton("Annulla")  { _, _ -> loadUsers() } // ripristina switch
            .setCancelable(false)
            .show()
    }

    private fun toggleUserAccess(username: String, hasAccess: Boolean) {
        val body: Map<String, Any> = mapOf("has_door_access" to hasAccess)
        RetrofitClient.getInstance(this).updateUser(username, body)
            .enqueue(object : Callback<GenericResponse> {
                override fun onResponse(call: Call<GenericResponse>, response: Response<GenericResponse>) {
                    if (response.isSuccessful) {
                        val msg = if (hasAccess) "✅ Accesso concesso a $username"
                        else           "🚫 Accesso revocato a $username"
                        Toast.makeText(this@UsersActivity, msg, Toast.LENGTH_SHORT).show()
                        loadUsers()
                    } else {
                        Toast.makeText(this@UsersActivity, "Errore aggiornamento", Toast.LENGTH_SHORT).show()
                        loadUsers()
                    }
                }
                override fun onFailure(call: Call<GenericResponse>, t: Throwable) {
                    Toast.makeText(this@UsersActivity, "Errore di connessione", Toast.LENGTH_SHORT).show()
                    loadUsers()
                }
            })
    }
}

// ── Adapter ──────────────────────────────────────────────────────────────────

class UserAdapter(
    private val users: List<UserItem>,
    private val onToggleAccess: (UserItem, Boolean) -> Unit
) : RecyclerView.Adapter<UserAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvInitial:    TextView      = view.findViewById(R.id.tvUserInitial)
        val tvUsername:   TextView      = view.findViewById(R.id.tvUsername)
        val chipRole:     Chip          = view.findViewById(R.id.chipUserRole)
        val chipAccess:   Chip          = view.findViewById(R.id.chipUserAccess)
        val switchAccess: SwitchMaterial = view.findViewById(R.id.switchUserAccess)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_user, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user      = users[position]
        val isAdmin   = user.is_admin ?: false
        val hasAccess = user.has_door_access

        holder.tvInitial.text  = user.username.first().uppercaseChar().toString()
        holder.tvUsername.text = user.username

        // Badge ruolo
        holder.chipRole.text = if (isAdmin) "Admin" else "Utente"
        holder.chipRole.setChipBackgroundColorResource(
            if (isAdmin) R.color.lilla_primary else R.color.gray_light
        )
        holder.chipRole.setTextColor(
            holder.itemView.context.getColor(
                if (isAdmin) R.color.white else R.color.gray_dark
            )
        )

        // Chip accesso
        holder.chipAccess.text = if (hasAccess) "Accesso" else "Negato"
        holder.chipAccess.setChipBackgroundColorResource(
            if (hasAccess) R.color.chip_access_bg else R.color.chip_denied_bg
        )
        holder.chipAccess.setTextColor(
            holder.itemView.context.getColor(
                if (hasAccess) R.color.chip_access_text else R.color.chip_denied_text
            )
        )

        // Avatar colore: viola se admin, grigio se utente base senza accesso
        holder.tvInitial.setBackgroundResource(
            when {
                isAdmin    -> R.drawable.bg_avatar          // viola
                hasAccess  -> R.drawable.bg_avatar          // viola
                else       -> R.drawable.bg_avatar_warning  // arancione = attenzione
            }
        )

        // Switch senza loop
        holder.switchAccess.setOnCheckedChangeListener(null)
        holder.switchAccess.isChecked = hasAccess
        holder.switchAccess.setOnCheckedChangeListener { _, checked ->
            onToggleAccess(user, checked)
        }
    }

    override fun getItemCount() = users.size
}