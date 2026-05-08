package com.example.progettoappiot

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class AccessoAdapter(private var list: List<Accesso>) :
    RecyclerView.Adapter<AccessoAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvUsername: TextView = view.findViewById(R.id.tvUsername)
        val tvOrario:   TextView = view.findViewById(R.id.orarioTextView)
        val tvAzione:   TextView = view.findViewById(R.id.tvAzione)
        val tvData:     TextView = view.findViewById(R.id.tvData)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_accesso, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val accesso = list[position]
        val ctx     = holder.itemView.context

        // Tutti i campi sono nullable — uso ?: per fallback sicuri
        holder.tvUsername.text = accesso.username?.ifEmpty { accesso.tag_id } ?: accesso.tag_id ?: "—"
        holder.tvOrario.text   = accesso.orario ?: "—"
        holder.tvData.text     = accesso.data ?: "—"

        val azione = accesso.azione?.lowercase() ?: ""
        when {
            azione == "aperta" -> {
                holder.tvAzione.text = "🔓 Aperta"
                holder.tvAzione.setTextColor(ContextCompat.getColor(ctx, R.color.door_open))
            }
            azione == "non autorizzato" -> {
                holder.tvAzione.text = "🚫 Negato"
                holder.tvAzione.setTextColor(ContextCompat.getColor(ctx, R.color.door_closed))
            }
            else -> {
                holder.tvAzione.text = "🔒 Bloccata"
                holder.tvAzione.setTextColor(ContextCompat.getColor(ctx, R.color.door_closed))
            }
        }
    }

    override fun getItemCount() = list.size

    fun updateData(newList: List<Accesso>) {
        list = newList
        notifyDataSetChanged()
    }
}