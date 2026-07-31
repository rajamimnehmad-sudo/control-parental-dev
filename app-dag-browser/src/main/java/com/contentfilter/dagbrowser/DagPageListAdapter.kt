package com.contentfilter.dagbrowser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

internal data class DagPageListItem(
    val title: String,
    val host: String,
    val detail: String,
    val url: String,
)

internal class DagPageListAdapter(
    private val onOpen: (DagPageListItem) -> Unit,
    private val onDelete: ((DagPageListItem) -> Unit)?,
) : RecyclerView.Adapter<DagPageListAdapter.ViewHolder>() {
    private val items = mutableListOf<DagPageListItem>()

    fun submit(newItems: List<DagPageListItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder =
        ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_dag_page_entry, parent, false),
        )

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.page_entry_title)
        private val host: TextView = view.findViewById(R.id.page_entry_host)
        private val detail: TextView = view.findViewById(R.id.page_entry_detail)
        private val delete: ImageButton = view.findViewById(R.id.page_entry_delete)

        fun bind(item: DagPageListItem) {
            title.text = item.title
            host.text = item.host
            detail.text = item.detail
            itemView.setOnClickListener { onOpen(item) }
            if (onDelete == null) {
                delete.visibility = View.GONE
            } else {
                delete.visibility = View.VISIBLE
                delete.setOnClickListener { onDelete.invoke(item) }
            }
        }
    }
}
