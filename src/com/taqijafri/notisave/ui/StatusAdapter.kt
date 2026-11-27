package com.taqijafri.notisave.ui

import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.taqijafri.notisave.R
import com.taqijafri.notisave.util.StatusFile
import com.taqijafri.notisave.util.StatusHelper

/** Grid adapter for WhatsApp statuses (images + videos). */
class StatusAdapter(
    private val context: Context,
    private var items: List<StatusFile>,
    private val onItemClick: (StatusFile) -> Unit,
    private val onSave: (StatusFile) -> Unit
) : BaseAdapter() {

    private val inflater = LayoutInflater.from(context)
    private val thumbCache = HashMap<String, Bitmap?>()
    private val savedCache = HashMap<String, Boolean>()

    fun update(newItems: List<StatusFile>) {
        items = newItems
        savedCache.clear()
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): Any = items[position]
    override fun getItemId(position: Int): Long = position.toLong()

    private fun thumbFor(status: StatusFile): Bitmap? {
        val key = status.uri.toString()
        if (!thumbCache.containsKey(key)) {
            thumbCache[key] = StatusHelper.thumbnail(context, status)
        }
        return thumbCache[key]
    }

    private fun isSaved(status: StatusFile): Boolean {
        if (status.isSavedCopy) return true
        if (!savedCache.containsKey(status.name)) {
            savedCache[status.name] = StatusHelper.isSaved(context, status)
        }
        return savedCache[status.name] == true
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_status, parent, false)
        val status = items[position]

        val thumb = view.findViewById<ImageView>(R.id.status_thumb)
        val play = view.findViewById<ImageView>(R.id.status_play)
        val savedBadge = view.findViewById<TextView>(R.id.status_saved_badge)
        val saveBtn = view.findViewById<ImageButton>(R.id.status_save_btn)
        val unseenDot = view.findViewById<View>(R.id.status_unseen_dot)

        val bitmap = thumbFor(status)
        if (bitmap != null) thumb.setImageBitmap(bitmap)
        else thumb.setImageResource(android.R.drawable.ic_menu_gallery)

        play.visibility = if (status.isVideo) View.VISIBLE else View.GONE
        val saved = isSaved(status)
        savedBadge.visibility = if (saved) View.VISIBLE else View.GONE
        saveBtn.visibility = if (saved) View.GONE else View.VISIBLE
        unseenDot.visibility = if (saved) View.GONE else View.VISIBLE

        view.setOnClickListener { onItemClick(status) }
        saveBtn.setOnClickListener { onSave(status) }
        return view
    }
}
