package com.example.progettoappiot

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class UsersActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: View
    private lateinit var tvEmpty: TextView
    private val userList = mutableListOf<UserItem>()
    private lateinit var adapter: UserAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_users)

        val toolbar = findViewById<Toolbar>(R.id.usersToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        recyclerView = findViewById(R.id.rvUsers)
        progressBar  = findViewById(R.id.progressUsers)
        tvEmpty      = findViewById(R.id.tvUsersEmpty)

        adapter = UserAdapter(userList) { user, hasAccess ->
            toggleUserAccess(user.username, hasAccess)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        loadUsers()
    }

    private fun loadUsers() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        tvEmpty.visibility = View.GONE

        RetrofitClient.getInstance(this).getUsers()
            .enqueue(object : Callback<List<UserItem>> {
                override fun onResponse(call: Call<List<UserItem>>, response: Response<List<UserItem>>) {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful) {
                        val users = response.body() ?: emptyList()
                        userList.clear()
                        userList.addAll(users)
                        adapter.notifyDataSetChanged()
                        if (users.isEmpty()) {
                            tvEmpty.visibility = View.VISIBLE
                        } else {
                            recyclerView.visibility = View.VISIBLE
                        }
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

    private fun toggleUserAccess(username: String, hasAccess: Boolean) {
        val body: Map<String, Any> = mapOf("has_door_access" to hasAccess)
        RetrofitClient.getInstance(this).updateUser(username, body)
            .enqueue(object : Callback<GenericResponse> {
                override fun onResponse(call: Call<GenericResponse>, response: Response<GenericResponse>) {
                    if (response.isSuccessful) {
                        val msg = if (hasAccess)
                            "✅ Accesso concesso a $username"
                        else
                            "🚫 Accesso revocato a $username"
                        Toast.makeText(this@UsersActivity, msg, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@UsersActivity, "Errore aggiornamento", Toast.LENGTH_SHORT).show()
                        loadUsers() // ripristina stato reale
                    }
                }

                override fun onFailure(call: Call<GenericResponse>, t: Throwable) {
                    Toast.makeText(this@UsersActivity, "Errore di connessione", Toast.LENGTH_SHORT).show()
                    loadUsers()
                }
            })
    }
}

class UserAdapter(
    private val users: List<UserItem>,
    private val onToggle: (UserItem, Boolean) -> Unit
) : RecyclerView.Adapter<UserAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvInitial: TextView         = view.findViewById(R.id.tvUserInitial)
        val tvUsername: TextView        = view.findViewById(R.id.tvUsername)
        val tvAccessLabel: TextView     = view.findViewById(R.id.tvAccessLabel)
        val switchAccess: SwitchMaterial = view.findViewById(R.id.switchUserAccess)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = users[position]
        holder.tvInitial.text  = user.username.first().uppercaseChar().toString()
        holder.tvUsername.text = user.username
        holder.tvAccessLabel.text = if (user.has_door_access) "Accesso consentito" else "Accesso negato"
        holder.tvAccessLabel.setTextColor(
            if (user.has_door_access)
                holder.itemView.context.getColor(R.color.access_granted)
            else
                holder.itemView.context.getColor(R.color.access_denied)
        )

        // Rimuove listener prima di impostare checked per evitare loop
        holder.switchAccess.setOnCheckedChangeListener(null)
        holder.switchAccess.isChecked = user.has_door_access
        holder.switchAccess.setOnCheckedChangeListener { _, isChecked ->
            onToggle(user, isChecked)
        }
    }

    override fun getItemCount() = users.size
}
