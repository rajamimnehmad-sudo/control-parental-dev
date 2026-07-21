package com.contentfilter.user.apps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.Base64
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.util.concurrent.Executors

@Composable
internal fun MyAppsNativeList(
    apps: List<MyAppItemUiState>,
    scrollResetKey: String,
    onRequestAccess: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            ListView(context).apply {
                clipToPadding = false
                setBackgroundColor(Color.TRANSPARENT)
                setPadding(0, 0, 0, context.dp(8))
                divider = null
                dividerHeight = 0
                isVerticalScrollBarEnabled = true
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                tag = scrollResetKey
                adapter = MyAppsListAdapter(context)
            }
        },
        update = { listView ->
            (listView.adapter as MyAppsListAdapter).submit(apps, onRequestAccess)
            if (listView.tag != scrollResetKey) {
                listView.tag = scrollResetKey
                listView.setSelection(0)
            }
        },
    )
}

private class MyAppsListAdapter(
    private val context: Context,
) : BaseAdapter() {
    private var apps: List<MyAppItemUiState> = emptyList()
    private var onRequestAccess: (String) -> Unit = {}

    override fun getCount(): Int = apps.size

    override fun getItem(position: Int): MyAppItemUiState = apps[position]

    override fun getItemId(position: Int): Long = apps[position].packageName.hashCode().toLong()

    override fun hasStableIds(): Boolean = true

    fun submit(
        nextApps: List<MyAppItemUiState>,
        nextOnRequestAccess: (String) -> Unit,
    ) {
        onRequestAccess = nextOnRequestAccess
        if (apps === nextApps || apps == nextApps) return
        apps = nextApps
        notifyDataSetChanged()
    }

    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup,
    ): View {
        val holder =
            (convertView?.tag as? AppRowHolder)
                ?: AppRowHolder.create(context).also { it.root.tag = it }
        val rowWidth =
            parent.measuredWidth.takeIf { it > 0 }
                ?: (context.resources.displayMetrics.widthPixels - context.dp(32))
        holder.root.layoutParams =
            AbsListView.LayoutParams(
                rowWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        holder.root.minimumWidth = rowWidth
        holder.bind(getItem(position), onRequestAccess)
        return holder.root
    }
}

private class AppRowHolder private constructor(
    val root: LinearLayout,
    private val icon: ImageView,
    private val fallback: TextView,
    private val name: TextView,
    private val limit: TextView,
    private val status: TextView,
    private val requestButton: Button,
) {
    fun bind(
        app: MyAppItemUiState,
        onRequestAccess: (String) -> Unit,
    ) {
        name.text = app.name
        limit.text = app.limitText
        status.text = app.status.nativeLabel(app.extraTimeRemainingMinutes)
        status.setTextColor(app.status.nativeColor())
        fallback.text = app.name.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
        bindIcon(app)

        val canRequest =
            app.status == AppAccessStatus.Blocked ||
                app.status == AppAccessStatus.RequiresAuthorization ||
                app.status == AppAccessStatus.LimitReached
        requestButton.visibility = if (canRequest || app.isRequesting) View.VISIBLE else View.GONE
        requestButton.isEnabled = canRequest && !app.isRequesting
        requestButton.text = if (app.isRequesting) "Enviando..." else "Pedir acceso"
        requestButton.setOnClickListener { onRequestAccess(app.packageName) }
    }

    private fun bindIcon(app: MyAppItemUiState) {
        val key = "${app.packageName}:${app.iconBase64?.hashCode() ?: 0}"
        icon.tag = key
        val cached = NativeIconCache.get(key)
        if (cached != null) {
            icon.setImageBitmap(cached)
            icon.visibility = View.VISIBLE
            fallback.visibility = View.GONE
            return
        }
        icon.setImageDrawable(null)
        icon.visibility = View.GONE
        fallback.visibility = View.VISIBLE
        val encoded = app.iconBase64 ?: return
        NativeIconCache.decode(key, encoded) { decoded ->
            if (icon.tag != key || decoded == null) return@decode
            icon.setImageBitmap(decoded)
            icon.visibility = View.VISIBLE
            fallback.visibility = View.GONE
        }
    }

    companion object {
        fun create(context: Context): AppRowHolder {
            val root =
                LinearLayout(context).apply {
                    layoutParams =
                        AbsListView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(Color.WHITE)
                }
            val contentRow =
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
                    minimumHeight = context.dp(68)
                }
            val iconFrame = FrameLayout(context)
            val icon =
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
            val fallback =
                TextView(context).apply {
                    gravity = Gravity.CENTER
                    textSize = 18f
                    setTextColor(Color.rgb(22, 34, 53))
                    setTypeface(typeface, Typeface.BOLD)
                    background =
                        GradientDrawable().apply {
                            color = android.content.res.ColorStateList.valueOf(Color.rgb(214, 244, 240))
                            shape = GradientDrawable.OVAL
                        }
                }
            iconFrame.addView(icon, FrameLayout.LayoutParams(context.dp(40), context.dp(40), Gravity.CENTER))
            iconFrame.addView(fallback, FrameLayout.LayoutParams(context.dp(40), context.dp(40), Gravity.CENTER))
            contentRow.addView(iconFrame, LinearLayout.LayoutParams(context.dp(50), context.dp(50)))

            val labels =
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                }
            val name =
                TextView(context).apply {
                    textSize = 16f
                    setTextColor(Color.rgb(22, 34, 53))
                    setTypeface(typeface, Typeface.BOLD)
                    maxLines = 1
                }
            val limit =
                TextView(context).apply {
                    textSize = 14f
                    setTextColor(Color.rgb(104, 117, 138))
                    maxLines = 1
                }
            labels.addView(name, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            labels.addView(limit, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            contentRow.addView(labels, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            val actions =
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.END
                    setPadding(context.dp(8), 0, 0, 0)
                }
            val status =
                TextView(context).apply {
                    textSize = 13f
                    gravity = Gravity.END
                }
            val requestButton =
                Button(context).apply {
                    textSize = 12f
                    isAllCaps = false
                    setTextColor(Color.rgb(0, 125, 190))
                    background = null
                    minHeight = 0
                    minimumHeight = 0
                    setPadding(context.dp(8), context.dp(2), 0, context.dp(2))
                }
            actions.addView(status, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            actions.addView(
                requestButton,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            contentRow.addView(actions, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            root.addView(
                contentRow,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            root.addView(
                View(context).apply { setBackgroundColor(Color.rgb(230, 235, 239)) },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    context.dp(1),
                ).apply {
                    marginStart = context.dp(62)
                },
            )

            return AppRowHolder(root, icon, fallback, name, limit, status, requestButton)
        }
    }
}

private object NativeIconCache {
    private val cache =
        object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
            override fun sizeOf(
                key: String,
                value: Bitmap,
            ): Int = value.allocationByteCount
        }
    private val decoder = Executors.newFixedThreadPool(2)

    fun get(key: String): Bitmap? = cache.get(key)

    fun decode(
        key: String,
        encoded: String,
        onDecoded: (Bitmap?) -> Unit,
    ) {
        decoder.execute {
            val bitmap =
                runCatching {
                    val bytes = Base64.decode(encoded, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }.getOrNull()
            if (bitmap != null) cache.put(key, bitmap)
            android.os.Handler(android.os.Looper.getMainLooper()).post { onDecoded(bitmap) }
        }
    }
}

private fun AppAccessStatus.nativeLabel(extraTimeRemainingMinutes: Int?): String =
    when (this) {
        AppAccessStatus.Allowed -> "Permitida"
        AppAccessStatus.Limited -> "Con límite"
        AppAccessStatus.LimitReached -> "Límite agotado"
        AppAccessStatus.ExtraTime -> extraTimeRemainingMinutes?.let { "Extra ${it}m" } ?: "Tiempo extra"
        AppAccessStatus.Blocked -> "Bloqueada"
        AppAccessStatus.RequiresAuthorization -> "Requiere permiso"
        AppAccessStatus.WaitingAuthorization -> "Esperando permiso"
        AppAccessStatus.WaitingExtraTime -> "Esperando tiempo"
    }

private fun AppAccessStatus.nativeColor(): Int =
    when (this) {
        AppAccessStatus.Allowed,
        AppAccessStatus.ExtraTime,
        -> Color.rgb(46, 125, 50)
        AppAccessStatus.Limited,
        AppAccessStatus.LimitReached,
        AppAccessStatus.WaitingExtraTime,
        -> Color.rgb(249, 168, 37)
        AppAccessStatus.Blocked,
        AppAccessStatus.RequiresAuthorization,
        AppAccessStatus.WaitingAuthorization,
        -> Color.rgb(198, 40, 40)
    }

private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
