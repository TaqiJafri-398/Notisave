package com.taqijafri.notisave.ui

import android.app.Activity
import android.app.Fragment
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.taqijafri.notisave.R
import com.taqijafri.notisave.util.StatusFile
import com.taqijafri.notisave.util.StatusHelper

/**
 * WhatsApp Status Saver: shows statuses you have viewed in WhatsApp,
 * lets you save any of them to Pictures/Notisave with one tap.
 *
 * Access works through the system folder picker (Storage Access Framework):
 * the user picks the WhatsApp folder once, no all-files permission needed.
 * Notification saving is untouched — it keeps running in its own tab.
 */
class StatusesFragment : Fragment() {

    private enum class Filter { ALL, IMAGES, VIDEOS, SAVED }

    private lateinit var adapter: StatusAdapter
    private lateinit var grid: GridView
    private lateinit var emptyState: View
    private lateinit var accessCard: View
    private lateinit var chipRow: LinearLayout
    private lateinit var countText: TextView
    private lateinit var saveAllBtn: Button
    private lateinit var toolbar: View
    private lateinit var newDot: View

    private var filter = Filter.ALL
    private var allStatuses: List<StatusFile> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_statuses, container, false)

        grid = view.findViewById(R.id.status_grid)
        emptyState = view.findViewById(R.id.status_empty)
        accessCard = view.findViewById(R.id.access_card)
        chipRow = view.findViewById(R.id.status_chip_row)
        countText = view.findViewById(R.id.status_count)
        saveAllBtn = view.findViewById(R.id.btn_save_all)
        toolbar = view.findViewById(R.id.status_toolbar)
        newDot = view.findViewById(R.id.status_new_dot)

        adapter = StatusAdapter(
            activity,
            emptyList(),
            onItemClick = { s -> openViewer(s) },
            onSave = { s -> saveOne(s) }
        )
        grid.adapter = adapter

        view.findViewById<Button>(R.id.btn_grant_storage).setOnClickListener {
            StatusHelper.openFolderPicker(this)
        }
        view.findViewById<Button>(R.id.btn_view_saved).setOnClickListener {
            filter = Filter.SAVED
            buildChips()
            applyFilter()
        }
        saveAllBtn.setOnClickListener {
            val unsaved = currentItems()
                .filter { !it.isSavedCopy && !StatusHelper.isSaved(activity, it) }
            if (unsaved.isEmpty()) {
                Toast.makeText(activity, R.string.status_already_saved, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val n = StatusHelper.saveAll(activity, unsaved)
            Toast.makeText(
                activity,
                getString(R.string.status_saved_toast) + " ($n)",
                Toast.LENGTH_SHORT
            ).show()
            reload()
        }
        return view
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == StatusHelper.PICK_FOLDER_REQUEST) {
            if (resultCode == Activity.RESULT_OK && data?.data != null) {
                val ok = StatusHelper.handleFolderPicked(activity, data.data!!)
                Toast.makeText(
                    activity,
                    if (ok) R.string.status_folder_picked else R.string.status_folder_failed,
                    Toast.LENGTH_SHORT
                ).show()
                reload()
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    fun reload() {
        if (!::grid.isInitialized) return
        val hasAccess = StatusHelper.hasFolderAccess(activity)
        accessCard.visibility = if (hasAccess) View.GONE else View.VISIBLE
        toolbar.visibility = if (hasAccess) View.VISIBLE else View.GONE

        allStatuses = if (hasAccess) StatusHelper.listStatuses(activity) else emptyList()
        buildChips()
        applyFilter()
    }

    private fun currentItems(): List<StatusFile> {
        return when (filter) {
            Filter.ALL -> allStatuses
            Filter.IMAGES -> allStatuses.filter { !it.isVideo }
            Filter.VIDEOS -> allStatuses.filter { it.isVideo }
            Filter.SAVED -> StatusHelper.listSaved(activity)
        }
    }

    private fun applyFilter() {
        val items = currentItems()
        adapter.update(items)
        val empty = items.isEmpty()
        grid.visibility = if (empty) View.GONE else View.VISIBLE
        emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        countText.text = getString(R.string.statuses_count, items.size)
        val unsavedCount = items.count { !it.isSavedCopy && !StatusHelper.isSaved(activity, it) }
        newDot.visibility = if (filter != Filter.SAVED && unsavedCount > 0) View.VISIBLE else View.GONE
        saveAllBtn.visibility =
            if (filter == Filter.SAVED || items.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun buildChips() {
        chipRow.removeAllViews()
        addChip(getString(R.string.filter_all), filter == Filter.ALL) {
            filter = Filter.ALL; buildChips(); applyFilter()
        }
        addChip(getString(R.string.filter_images), filter == Filter.IMAGES) {
            filter = Filter.IMAGES; buildChips(); applyFilter()
        }
        addChip(getString(R.string.filter_videos), filter == Filter.VIDEOS) {
            filter = Filter.VIDEOS; buildChips(); applyFilter()
        }
        addChip(getString(R.string.filter_saved), filter == Filter.SAVED) {
            filter = Filter.SAVED; buildChips(); applyFilter()
        }
    }

    private fun addChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
        val ta = activity.obtainStyledAttributes(intArrayOf(R.attr.textPrimary, R.attr.inkOnFilled))
        val primaryColor = ta.getColor(0, Color.BLACK)
        val onFilledColor = ta.getColor(1, Color.WHITE)
        ta.recycle()
        val chip = TextView(activity).apply {
            text = label
            textSize = 13f
            setPadding(32, 16, 32, 16)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = 16
            layoutParams = lp
            if (isSelected) {
                setBackgroundResource(R.drawable.bg_chip_selected)
                setTextColor(onFilledColor)
            } else {
                setBackgroundResource(R.drawable.bg_chip)
                setTextColor(primaryColor)
            }
            setOnClickListener { onClick() }
        }
        chipRow.addView(chip)
    }

    private fun saveOne(status: StatusFile) {
        if (StatusHelper.isSaved(activity, status)) {
            Toast.makeText(activity, R.string.status_already_saved, Toast.LENGTH_SHORT).show()
            return
        }
        val saved = StatusHelper.saveStatus(activity, status)
        if (saved != null) {
            Toast.makeText(activity, R.string.status_saved_toast, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(activity, R.string.status_save_failed, Toast.LENGTH_SHORT).show()
        }
        reload()
    }

    private fun openViewer(status: StatusFile) {
        val intent = Intent(activity, StatusViewerActivity::class.java)
        intent.putExtra(StatusViewerActivity.EXTRA_URI, status.uri.toString())
        intent.putExtra(StatusViewerActivity.EXTRA_NAME, status.name)
        intent.putExtra(StatusViewerActivity.EXTRA_IS_VIDEO, status.isVideo)
        intent.putExtra(StatusViewerActivity.EXTRA_IS_SAVED_COPY, status.isSavedCopy)
        intent.putExtra(StatusViewerActivity.EXTRA_MODIFIED, status.lastModified)
        intent.putExtra(StatusViewerActivity.EXTRA_SIZE, status.size)
        startActivity(intent)
    }
}
