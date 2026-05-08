package com.example.progettoappiot

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class StoricoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_storico)

        val toolbar  = findViewById<Toolbar>(R.id.storicoToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val recycler  = findViewById<RecyclerView>(R.id.recyclerStorico)
        val progress  = findViewById<ProgressBar>(R.id.progressStorico)
        val tvInfo    = findViewById<TextView>(R.id.tvStoricoInfo)

        val adapter = AccessoAdapter(emptyList())
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter       = adapter

        progress.visibility = View.VISIBLE

        RetrofitClient.getInstance(this)
            .getAccessi(limit = 50)
            .enqueue(object : Callback<List<Accesso>> {
                override fun onResponse(
                    call: Call<List<Accesso>>,
                    response: Response<List<Accesso>>
                ) {
                    progress.visibility = View.GONE
                    if (response.isSuccessful) {
                        val list = response.body() ?: emptyList()
                        adapter.updateData(list)
                        tvInfo.text = "Ultimi ${list.size} accessi registrati"
                    } else {
                        tvInfo.text = "Errore nel caricamento"
                    }
                }
                override fun onFailure(call: Call<List<Accesso>>, t: Throwable) {
                    progress.visibility = View.GONE
                    tvInfo.text = "Errore connessione"
                    Toast.makeText(this@StoricoActivity, "Errore: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }
}
