package com.contentfilter.dagbrowser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.DateFormat
import java.util.Date

internal class DagDownloadsAdapter(
    private val onOpen: (File) -> Unit,
    private val onDelete: (File) -> Unit,
) : RecyclerView.Adapter<DagDownloadsAdapter.ViewHolder>() {
    private val files = mutableListOf<File>()

    fun submit(newFiles: List<File>) {
        files.clear()
        files.addAll(newFiles)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder =
        ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_dag_download, parent, false),
        )

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        holder.bind(files[position])
    }

    override fun getItemCount(): Int = files.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.download_name)
        private val detail: TextView = view.findViewById(R.id.download_detail)
        private val open: Button = view.findViewById(R.id.download_open)
        private val delete: ImageButton = view.findViewById(R.id.download_delete)

        fun bind(file: File) {
            name.text = file.name
            val modifiedAt =
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(Date(file.lastModified()))
            detail.text = "${readableByteCount(file.length())} · $modifiedAt"
            open.setOnClickListener { onOpen(file) }
            delete.setOnClickListener { onDelete(file) }
        }
    }

    private fun readableByteCount(bytes: Long): String =
        if (bytes < 1024 * 1024) {
            "${(bytes / 1024).coerceAtLeast(1)} KB"
        } else {
            "%.1f MB".format(bytes / (1024.0 * 1024.0))
        }
}
