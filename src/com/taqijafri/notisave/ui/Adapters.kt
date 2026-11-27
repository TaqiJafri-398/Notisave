package com.taqijafri.notisave.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import com.taqijafri.notisave.R
import com.taqijafri.notisave.data.AppEntry
import com.taqijafri.notisave.data.SavedNotification
import com.taqijafri.notisave.data.Prefs
import com.taqijafri.notisave.util.AppInfo
import com.taqijafri.notisave.util.TimeUtils

/** List adapter for saved notifications (newest first). */
class NotificationAdapter(
    private val context: Context,
    private var items: List<SavedNotification>,
    private val onItemClick: (SavedNotification) -> Unit,
    private val onDelete: (SavedNotification) -> Unit
) : BaseAdapter() {

    private val inflater = LayoutInflater.from(context)
    private val iconCache = HashMap<String, Drawable?>()
    private val redactPreviews = Prefs(context).redactPreviews

    fun update(newItems: List<SavedNotification>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): Any = items[position]
    override fun getItemId(position: Int): Long = items[position].id

    private fun iconFor(packageName: String): Drawable? {
        if (!iconCache.containsKey(packageName)) {
            iconCache[packageName] = AppInfo.getAppIcon(context.packageManager, packageName)
        }
        return iconCache[packageName]
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_notification, parent, false)
        val n = items[position]

        val icon = view.findViewById<ImageView>(R.id.item_icon)
        val appName = view.findViewById<TextView>(R.id.item_app_name)
        val time = view.findViewById<TextView>(R.id.item_time)
        val title = view.findViewById<TextView>(R.id.item_title)
        val text = view.findViewById<TextView>(R.id.item_text)
        val dot = view.findViewById<View>(R.id.item_unread_dot)
        val deleteBtn = view.findViewById<ImageButton>(R.id.item_delete)

        val drawable = iconFor(n.packageName)
        if (drawable != null) icon.setImageDrawable(drawable)
        else icon.setImageResource(android.R.drawable.sym_def_app_icon)

        appName.text = n.appName
        time.text = TimeUtils.relativeTime(n.postTime)
        title.text = if (redactPreviews) context.getString(R.string.preview_hidden)
            else if (n.title.isNotBlank()) n.title else n.appName
        text.text = if (redactPreviews) context.getString(R.string.preview_hidden) else n.text
        dot.visibility = if (n.isRead) View.GONE else View.VISIBLE

        view.setOnClickListener { onItemClick(n) }
        deleteBtn.setOnClickListener { onDelete(n) }
        return view
    }
}

/** List adapter for the Apps tab: icon, name, saved count, save toggle. */
class AppAdapter(
    private val context: Context,
    private var items: List<AppEntry>,
    private val onToggle: (AppEntry, Boolean) -> Unit
) : BaseAdapter() {

    private val inflater = LayoutInflater.from(context)
    private val iconCache = HashMap<String, Drawable?>()

    fun update(newItems: List<AppEntry>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): Any = items[position]
    override fun getItemId(position: Int): Long = position.toLong()

    private fun iconFor(packageName: String): Drawable? {
        if (!iconCache.containsKey(packageName)) {
            iconCache[packageName] = AppInfo.getAppIcon(context.packageManager, packageName)
        }
        return iconCache[packageName]
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_app, parent, false)
        val entry = items[position]

        val icon = view.findViewById<ImageView>(R.id.app_icon)
        val name = view.findViewById<TextView>(R.id.app_name)
        val pkg = view.findViewById<TextView>(R.id.app_package)
        val saved = view.findViewById<TextView>(R.id.app_saved)
        val switch = view.findViewById<Switch>(R.id.app_switch)
        val label = view.findViewById<TextView>(R.id.app_switch_label)

        val drawable = iconFor(entry.packageName)
        if (drawable != null) icon.setImageDrawable(drawable)
        else icon.setImageResource(android.R.drawable.sym_def_app_icon)

        name.text = entry.appName
        pkg.text = entry.packageName
        saved.text = context.getString(R.string.saved_count, entry.savedCount)
        label.text = context.getString(
            if (entry.enabled) R.string.saving_on else R.string.saving_off
        )

        // Set state without firing the listener (view recycling).
        switch.setOnCheckedChangeListener(null)
        switch.isChecked = entry.enabled
        switch.setOnCheckedChangeListener { _, isChecked ->
            label.text = context.getString(
                if (isChecked) R.string.saving_on else R.string.saving_off
            )
            onToggle(entry, isChecked)
        }
        return view
    }
}
