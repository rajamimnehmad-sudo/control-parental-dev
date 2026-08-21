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
import androidx.compose.ui.draw.clipToBounds
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
        modifier = modifier.clipToBounds(),
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
        val holder = (convertView?.tag as? AppRowHolder) ?: AppRowHolder.create(context).also { it.root.tag = it }
        val rowWidth = parent.measuredWidth.takeIf { it > 0 } ?: (context.resources.displayMetrics.widthPixels - context.dp(32))
        holder.root.layoutParams = AbsListView.LayoutParams(rowWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
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
        requestButton.alpha = if (requestButton.isEnabled) 1f else 0.55f
        requestButton.text = if (app.isRequesting) "Enviando…" else "Pedir acceso"
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
                    layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(Surface)
                }
            val contentRow =
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(context.dp(14), context.dp(10), context.dp(14), context.dp(10))
                    minimumHeight = context.dp(72)
                }
            val iconFrame = FrameLayout(context)
            val icon = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            val fallback =
                TextView(context).apply {
                    gravity = Gravity.CENTER
                    textSize = 17f
                    setTextColor(Graphite)
                    setTypeface(typeface, Typeface.BOLD)
                    background =
                        GradientDrawable().apply {
                            color = android.content.res.ColorStateList.valueOf(LimeSoft)
                            shape = GradientDrawable.OVAL
                        }
                }
            iconFrame.addView(icon, FrameLayout.LayoutParams(context.dp(42), context.dp(42), Gravity.CENTER))
            iconFrame.addView(fallback, FrameLayout.LayoutParams(context.dp(42), context.dp(42), Gravity.CENTER))
            contentRow.addView(iconFrame, LinearLayout.LayoutParams(context.dp(54), context.dp(54)))

            val labels = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
            val name =
                TextView(context).apply {
                    textSize = 16f
                    setTextColor(Graphite)
                    setTypeface(typeface, Typeface.BOLD)
                    maxLines = 1
                }
            val limit =
                TextView(context).apply {
                    textSize = 13f
                    setTextColor(Muted)
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
            val status = TextView(context).apply { textSize = 12f; gravity = Gravity.END }
            val requestButton =
                Button(context).apply {
                    textSize = 12f
                    isAllCaps = false
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Graphite)
                    minHeight = 0
                    minimumHeight = 0
                    minWidth = 0
                    minimumWidth = 0
                    setPadding(context.dp(12), context.dp(6), context.dp(12), context.dp(6))
                    background =
                        GradientDrawable().apply {
                            color = android.content.res.ColorStateList.valueOf(Lime)
                            cornerRadius = context.dp(999).toFloat()
                        }
                }
            actions.addView(status, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            actions.addView(requestButton, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            contentRow.addView(actions, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            root.addView(contentRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            root.addView(
                View(context).apply { setBackgroundColor(Line) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(1)).apply { marginStart = context.dp(68) },
            )

            return AppRowHolder(root, icon, fallback, name, limit, status, requestButton)
        }
    }
}

private object NativeIconCache {
    private val cache =
        object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
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
        AppAccessStatus.RequiresAuthorization -> "Necesita permiso"
        AppAccessStatus.WaitingAuthorization -> "Esperando permiso"
        AppAccessStatus.WaitingExtraTime -> "Esperando tiempo"
    }

private fun AppAccessStatus.nativeColor(): Int =
    when (this) {
        AppAccessStatus.Allowed,
        AppAccessStatus.ExtraTime,
        -> Positive
        AppAccessStatus.Limited,
        AppAccessStatus.LimitReached,
        AppAccessStatus.WaitingExtraTime,
        -> Warning
        AppAccessStatus.Blocked,
        AppAccessStatus.RequiresAuthorization,
        AppAccessStatus.WaitingAuthorization,
        -> Danger
    }

private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

private val Surface = Color.rgb(255, 255, 255)
private val Graphite = Color.rgb(23, 26, 24)
private val Muted = Color.rgb(116, 121, 112)
private val Line = Color.rgb(229, 227, 220)
private val Lime = Color.rgb(200, 243, 29)
private val LimeSoft = Color.rgb(240, 248, 200)
private val Positive = Color.rgb(36, 122, 75)
private val Warning = Color.rgb(148, 105, 0)
private val Danger = Color.rgb(180, 35, 24)
