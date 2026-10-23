package com.example.dnevnik

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PeopleAdapter(
    private var peopleList: List<PersonEntity>,
    private val onPersonClick: (PersonEntity) -> Unit,
    private val onPinClick: (PersonEntity) -> Unit
) : RecyclerView.Adapter<PeopleAdapter.PeopleViewHolder>() {

    class PeopleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvPersonName)
        val ivPinned: ImageView = view.findViewById(R.id.ivPinnedPerson)

        // ШАГ 2: Добавили поле для подсказки под именем
        val tvHint: TextView = view.findViewById(R.id.tvPersonHint)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PeopleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_person, parent, false)
        return PeopleViewHolder(view)
    }

    override fun onBindViewHolder(holder: PeopleViewHolder, position: Int) {
        val person = peopleList[position]
        holder.tvName.text = person.name

        // ШАГ 2: Устанавливаем текст подсказки для каждой карточки в списке
        holder.tvHint.text = "Нажмите, чтобы увидеть полную информацию"

        // Видимость булавки
        holder.ivPinned.visibility = if (person.isPinned) View.VISIBLE else View.GONE

        // Клик на элемент открывает детальную информацию
        holder.itemView.setOnClickListener { onPersonClick(person) }
    }

    override fun getItemCount(): Int = peopleList.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newPeople: List<PersonEntity>) {
        this.peopleList = newPeople
        notifyDataSetChanged()
    }
}