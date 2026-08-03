package com.contentfilter.dagbrowser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import java.util.Collections

internal data class DagTabCard(
    val id: Long,
    val title: String,
    val host: String,
    val active: Boolean,
    val thumbnail: Bitmap?,
)

internal class DagTabSwitcherView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : FrameLayout(context, attrs) {
        interface Listener {
            fun onTabSelected(tabId: Long)

            fun onTabClosed(tabId: Long)

            fun onNewTab()

            fun onCloseAllTabs()

            fun onTabsReordered(tabIds: List<Long>)

            fun onSwitcherClosed()
        }

        private val title: TextView
        private val closeAll: View
        private val recycler: RecyclerView
        private val adapter = TabAdapter()
        private var listener: Listener? = null
        private var headerDownY = 0f

        init {
            LayoutInflater.from(context).inflate(R.layout.view_dag_tab_switcher, this, true)
            title = findViewById(R.id.tab_switcher_title)
            closeAll = findViewById(R.id.tab_switcher_close_all)
            recycler =
                findViewById<RecyclerView>(R.id.tab_switcher_grid).apply {
                    layoutManager = GridLayoutManager(context, 2)
                    adapter = this@DagTabSwitcherView.adapter
                    setHasFixedSize(true)
                    addItemDecoration(GridSpacingDecoration(dp(6)))
                }
            findViewById<View>(R.id.tab_switcher_new).setOnClickListener {
                listener?.onNewTab()
            }
            findViewById<View>(R.id.tab_switcher_done).setOnClickListener {
                listener?.onSwitcherClosed()
            }
            findViewById<View>(R.id.tab_switcher_header).setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        headerDownY = event.y
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (event.y - headerDownY >= dp(48)) {
                            listener?.onSwitcherClosed()
                        } else {
                            view.performClick()
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> true
                    else -> true
                }
            }
            closeAll.setOnClickListener {
                listener?.onCloseAllTabs()
            }
            ItemTouchHelper(
                object : ItemTouchHelper.SimpleCallback(
                    ItemTouchHelper.UP or
                        ItemTouchHelper.DOWN or
                        ItemTouchHelper.LEFT or
                        ItemTouchHelper.RIGHT,
                    ItemTouchHelper.START or ItemTouchHelper.END,
                ) {
                    override fun onMove(
                        recyclerView: RecyclerView,
                        viewHolder: RecyclerView.ViewHolder,
                        target: RecyclerView.ViewHolder,
                    ): Boolean {
                        val from = viewHolder.bindingAdapterPosition
                        val to = target.bindingAdapterPosition
                        if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) {
                            return false
                        }
                        adapter.move(from, to)
                        listener?.onTabsReordered(adapter.ids())
                        return true
                    }

                    override fun onSwiped(
                        viewHolder: RecyclerView.ViewHolder,
                        direction: Int,
                    ) {
                        val position = viewHolder.bindingAdapterPosition
                        val tabId = adapter.idAt(position)
                        if (tabId != null) {
                            listener?.onTabClosed(tabId)
                        }
                    }
                },
            ).attachToRecyclerView(recycler)
            visibility = GONE
        }

        fun setListener(listener: Listener) {
            this.listener = listener
        }

        fun show(tabs: List<DagTabCard>) {
            render(tabs)
            if (visibility != VISIBLE) {
                alpha = 0f
                visibility = VISIBLE
                animate().alpha(1f).setDuration(120L).start()
            }
        }

        fun render(tabs: List<DagTabCard>) {
            title.text =
                resources.getQuantityString(
                    R.plurals.open_tabs_count,
                    tabs.size,
                    tabs.size,
                )
            adapter.submit(tabs)
            closeAll.isEnabled = tabs.size > 1
            closeAll.alpha = if (tabs.size > 1) 1f else 0.45f
        }

        fun hide() {
            if (visibility != VISIBLE) return
            animate()
                .alpha(0f)
                .setDuration(100L)
                .withEndAction {
                    visibility = GONE
                    alpha = 1f
                }
                .start()
        }

        fun isOpen(): Boolean = visibility == VISIBLE

        private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

        private inner class TabAdapter : RecyclerView.Adapter<TabViewHolder>() {
            private val items = mutableListOf<DagTabCard>()

            init {
                setHasStableIds(true)
            }

            override fun getItemId(position: Int): Long = items[position].id

            override fun onCreateViewHolder(
                parent: ViewGroup,
                viewType: Int,
            ): TabViewHolder {
                val view =
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_dag_tab, parent, false)
                return TabViewHolder(view)
            }

            override fun onBindViewHolder(
                holder: TabViewHolder,
                position: Int,
            ) {
                holder.bind(items[position])
            }

            override fun getItemCount(): Int = items.size

            fun submit(tabs: List<DagTabCard>) {
                val previous = items.toList()
                val diff =
                    DiffUtil.calculateDiff(
                        object : DiffUtil.Callback() {
                            override fun getOldListSize(): Int = previous.size

                            override fun getNewListSize(): Int = tabs.size

                            override fun areItemsTheSame(
                                oldItemPosition: Int,
                                newItemPosition: Int,
                            ): Boolean = previous[oldItemPosition].id == tabs[newItemPosition].id

                            override fun areContentsTheSame(
                                oldItemPosition: Int,
                                newItemPosition: Int,
                            ): Boolean = previous[oldItemPosition] == tabs[newItemPosition]
                        },
                    )
                items.clear()
                items.addAll(tabs)
                diff.dispatchUpdatesTo(this)
            }

            fun move(
                from: Int,
                to: Int,
            ) {
                Collections.swap(items, from, to)
                notifyItemMoved(from, to)
            }

            fun ids(): List<Long> = items.map(DagTabCard::id)

            fun idAt(position: Int): Long? = items.getOrNull(position)?.id
        }

        private inner class TabViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val title: TextView = view.findViewById(R.id.tab_card_title)
            private val host: TextView = view.findViewById(R.id.tab_card_host)
            private val close: ImageButton = view.findViewById(R.id.tab_card_close)
            private val preview: ImageView = view.findViewById(R.id.tab_card_preview)
            private val placeholder: TextView = view.findViewById(R.id.tab_card_placeholder)

            fun bind(tab: DagTabCard) {
                title.text = tab.title
                host.text = tab.host
                itemView.background =
                    GradientDrawable().apply {
                        setColor(context.getColor(R.color.dag_surface))
                        cornerRadius = dp(18).toFloat()
                        setStroke(
                            dp(if (tab.active) 3 else 1),
                            context.getColor(
                                if (tab.active) R.color.dag_blue else R.color.dag_border,
                            ),
                        )
                    }
                itemView.contentDescription =
                    if (tab.active) {
                        "${tab.title}, ${context.getString(R.string.active_tab)}"
                    } else {
                        tab.title
                    }
                preview.setImageBitmap(tab.thumbnail)
                preview.visibility = if (tab.thumbnail == null) INVISIBLE else VISIBLE
                placeholder.visibility = if (tab.thumbnail == null) VISIBLE else GONE
                placeholder.text =
                    if (tab.host == context.getString(R.string.new_tab_title)) {
                        context.getString(R.string.browser_brand)
                    } else {
                        tab.host
                    }
                itemView.setOnClickListener {
                    listener?.onTabSelected(tab.id)
                }
                close.contentDescription =
                    context.getString(R.string.close_named_tab, tab.title)
                close.setOnClickListener {
                    listener?.onTabClosed(tab.id)
                }
            }
        }

        private class GridSpacingDecoration(
            private val halfSpace: Int,
        ) : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State,
            ) {
                outRect.set(halfSpace, halfSpace, halfSpace, halfSpace)
            }
        }
    }
