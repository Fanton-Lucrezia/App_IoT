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

    private val allUsers      = mutableListOf<UserItem>()
    private val filteredUsers = mutableListOf<UserItem>()
    private val pendingResets = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_users)

        val toolbar = findViewById<Toolbar>(R.id.usersToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        recyclerView = findViewById(R.id.rvUsers)
        progressBar  = findViewById(R.id.progressUsers)
        emptyState   = findViewById(R.id.emptyStateUsers)
        tvUserCount  = findViewById(R.id.tvUserCount)
        etSearch     = findViewById(R.id.etSearchUsers)

        adapter = UserAdapter(
            users          = filteredUsers,
            pendingResets  = pendingResets,
            onToggleAccess = { user, hasAccess -> confirmToggleAccess(user, hasAccess) },
            onDelete       = { user -> showDeleteUserDialog(user) },
            onApproveReset = { username -> approveReset(username) },
            onRejectReset  = { username -> rejectReset(username) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

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
                        allUsers.addAll(users.sortedWith(
                            compareByDescending<UserItem> { it.is_admin ?: false }
                                .thenBy { it.username }
                        ))
                        filterUsers(etSearch.text.toString())
                        loadPendingResets()
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

    private fun loadPendingResets() {
        RetrofitClient.getInstance(this).getPendingResets()
            .enqueue(object : Callback<List<String>> {
                override fun onResponse(call: Call<List<String>>, response: Response<List<String>>) {
                    pendingResets.clear()
                    pendingResets.addAll(response.body() ?: emptyList())
                    adapter.notifyDataSetChanged()
                }
                override fun onFailure(call: Call<List<String>>, t: Throwable) {}
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
        emptyState.visibility   = if (hasResults) View.GONE   else View.VISIBLE
    }

    private fun updateUserCount() {
        val total  = allUsers.size
        val active = allUsers.count { it.has_door_access }
        tvUserCount.text = "$total utenti · $active con accesso"
    }

    // ── Toggle accesso ────────────────────────────────────────────────────────

    private fun confirmToggleAccess(user: UserItem, hasAccess: Boolean) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_access, null)
        dialogView.findViewById<TextView>(R.id.tvDialogIcon).text    = if (hasAccess) "✅" else "🚫"
        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text   = if (hasAccess) "Concedi accesso" else "Revoca accesso"
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text =
            if (hasAccess) "Vuoi dare l'accesso alla porta a «${user.username}»?"
            else           "Vuoi revocare l'accesso alla porta a «${user.username}»?"
        dialogView.findViewById<TextView>(R.id.btnConfirm).text = "Sì"
        dialogView.findViewById<TextView>(R.id.btnCancel).text  = "No"

        val dialog = AlertDialog.Builder(this, R.style.TransparentDialog)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialogView.findViewById<View>(R.id.btnConfirm).setOnClickListener {
            dialog.dismiss()
            toggleUserAccess(user.username, hasAccess)
        }
        dialogView.findViewById<View>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
            loadUsers()
        }
        dialog.show()
    }

    private fun toggleUserAccess(username: String, hasAccess: Boolean) {
        RetrofitClient.getInstance(this)
            .updateUser(username, mapOf("has_door_access" to hasAccess))
            .enqueue(object : Callback<GenericResponse> {
                override fun onResponse(call: Call<GenericResponse>, response: Response<GenericResponse>) {
                    val msg = if (hasAccess) "✅ Accesso concesso a $username"
                    else           "🚫 Accesso revocato a $username"
                    Toast.makeText(this@UsersActivity,
                        if (response.isSuccessful) msg else "Errore aggiornamento",
                        Toast.LENGTH_SHORT).show()
                    loadUsers()
                }
                override fun onFailure(call: Call<GenericResponse>, t: Throwable) {
                    Toast.makeText(this@UsersActivity, "Errore di connessione", Toast.LENGTH_SHORT).show()
                    loadUsers()
                }
            })
    }

    // ── Elimina utente ────────────────────────────────────────────────────────

    private fun showDeleteUserDialog(user: UserItem) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_access, null)
        dialogView.findViewById<TextView>(R.id.tvDialogIcon).text    = "🗑️"
        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text   = "Elimina utente"
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text =
            "Vuoi eliminare l'utente «${user.username}»?\nL'operazione non è reversibile."
        dialogView.findViewById<TextView>(R.id.btnConfirm).text = "Sì"
        dialogView.findViewById<TextView>(R.id.btnCancel).text  = "No"

        val dialog = AlertDialog.Builder(this, R.style.TransparentDialog)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialogView.findViewById<View>(R.id.btnCancel).setOnClickListener  { dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btnConfirm).setOnClickListener {
            dialog.dismiss()
            deleteUser(user)
        }
        dialog.show()
    }

    private fun deleteUser(user: UserItem) {
        RetrofitClient.getInstance(this).deleteUser(user.username)
            .enqueue(object : Callback<GenericResponse> {
                override fun onResponse(call: Call<GenericResponse>, response: Response<GenericResponse>) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@UsersActivity, "🗑️ Utente eliminato", Toast.LENGTH_SHORT).show()
                        loadUsers()
                    } else {
                        val msg = response.body()?.message ?: "Errore eliminazione"
                        Toast.makeText(this@UsersActivity, msg, Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<GenericResponse>, t: Throwable) {
                    Toast.makeText(this@UsersActivity, "Errore di connessione", Toast.LENGTH_SHORT).show()
                }
            })
    }

    // ── Reset password ────────────────────────────────────────────────────────

    private fun approveReset(username: String) {
        RetrofitClient.getInstance(this)
            .approveReset(mapOf("username" to username))
            .enqueue(object : Callback<GenericResponse> {
                override fun onResponse(call: Call<GenericResponse>, response: Response<GenericResponse>) {
                    Toast.makeText(this@UsersActivity, "✅ Password di $username reimpostata", Toast.LENGTH_SHORT).show()
                    loadUsers()
                    loadPendingResets()
                }
                override fun onFailure(call: Call<GenericResponse>, t: Throwable) {
                    Toast.makeText(this@UsersActivity, "Errore di connessione", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun rejectReset(username: String) {
        RetrofitClient.getInstance(this)
            .rejectReset(mapOf("username" to username))
            .enqueue(object : Callback<GenericResponse> {
                override fun onResponse(call: Call<GenericResponse>, response: Response<GenericResponse>) {
                    Toast.makeText(this@UsersActivity, "🚫 Richiesta rifiutata", Toast.LENGTH_SHORT).show()
                    loadUsers()
                    loadPendingResets()
                }
                override fun onFailure(call: Call<GenericResponse>, t: Throwable) {
                    Toast.makeText(this@UsersActivity, "Errore di connessione", Toast.LENGTH_SHORT).show()
                }
            })
    }
}

// ── Adapter ───────────────────────────────────────────────────────────────────

class UserAdapter(
    private val users: List<UserItem>,
    private val pendingResets: List<String>,
    private val onToggleAccess: (UserItem, Boolean) -> Unit,
    private val onDelete: (UserItem) -> Unit,
    private val onApproveReset: (String) -> Unit,
    private val onRejectReset: (String) -> Unit
) : RecyclerView.Adapter<UserAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvInitial:      TextView       = view.findViewById(R.id.tvUserInitial)
        val tvUsername:     TextView       = view.findViewById(R.id.tvUsername)
        val chipRole:       Chip           = view.findViewById(R.id.chipUserRole)
        val chipAccess:     Chip           = view.findViewById(R.id.chipUserAccess)
        val switchAccess:   SwitchMaterial = view.findViewById(R.id.switchUserAccess)
        val btnDelete:      View           = view.findViewById(R.id.btnDeleteUser)
        val tvResetRequest: TextView       = view.findViewById(R.id.tvResetRequest)
        val btnApprove:     View           = view.findViewById(R.id.btnApproveReset)
        val btnReject:      View           = view.findViewById(R.id.btnRejectReset)
        val ivAvatar: com.google.android.material.imageview.ShapeableImageView =
            view.findViewById(R.id.ivUserAvatar)
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

        holder.chipRole.text = if (isAdmin) "Admin" else "Utente"
        holder.chipRole.setChipBackgroundColorResource(
            if (isAdmin) R.color.lilla_primary else R.color.gray_light
        )
        holder.chipRole.setTextColor(
            holder.itemView.context.getColor(if (isAdmin) R.color.white else R.color.gray_dark)
        )

        holder.chipAccess.text = if (hasAccess) "Accesso" else "Negato"
        holder.chipAccess.setChipBackgroundColorResource(
            if (hasAccess) R.color.chip_access_bg else R.color.chip_denied_bg
        )
        holder.chipAccess.setTextColor(
            holder.itemView.context.getColor(
                if (hasAccess) R.color.chip_access_text else R.color.chip_denied_text
            )
        )

        holder.tvInitial.setBackgroundResource(
            when {
                isAdmin   -> R.drawable.bg_avatar
                hasAccess -> R.drawable.bg_avatar
                else      -> R.drawable.bg_avatar_warning
            }
        )
        // Foto profilo: mostra se disponibile, altrimenti mostra l'iniziale
        val pic = user.profile_picture
        if (!pic.isNullOrEmpty()) {
            try {
                val bytes  = android.util.Base64.decode(pic, android.util.Base64.NO_WRAP)
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                holder.ivAvatar.setImageBitmap(bitmap)
                holder.ivAvatar.visibility  = View.VISIBLE
                holder.tvInitial.visibility = View.INVISIBLE
            } catch (_: Exception) {
                holder.ivAvatar.visibility  = View.GONE
                holder.tvInitial.visibility = View.VISIBLE
            }
        } else {
            holder.ivAvatar.visibility  = View.GONE
            holder.tvInitial.visibility = View.VISIBLE
        }

        holder.switchAccess.setOnCheckedChangeListener(null)
        holder.switchAccess.isChecked = hasAccess
        holder.switchAccess.setOnCheckedChangeListener { _, checked ->
            onToggleAccess(user, checked)
        }

        holder.btnDelete.visibility = if (isAdmin) View.GONE else View.VISIBLE
        holder.btnDelete.setOnClickListener { onDelete(user) }

        val hasPendingReset = pendingResets.contains(user.username)
        holder.tvResetRequest.visibility = if (hasPendingReset) View.VISIBLE else View.GONE
        holder.btnApprove.visibility     = if (hasPendingReset) View.VISIBLE else View.GONE
        holder.btnReject.visibility      = if (hasPendingReset) View.VISIBLE else View.GONE

        holder.btnApprove.setOnClickListener { onApproveReset(user.username) }
        holder.btnReject.setOnClickListener  { onRejectReset(user.username) }
    }

    override fun getItemCount() = users.size
}