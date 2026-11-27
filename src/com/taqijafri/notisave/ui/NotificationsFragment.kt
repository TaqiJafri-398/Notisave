package com.taqijafri.notisave.ui

import android.app.AlertDialog
import android.app.Fragment
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import android.widget.Spinner
import android.widget.AdapterView
import java.util.Calendar
import com.taqijafri.notisave.R
import com.taqijafri.notisave.data.NotiDbHelper
import com.taqijafri.notisave.data.SavedNotification
import com.taqijafri.notisave.util.Constants

/**
 * Full history: newest first, with search (app / sender / message),
 * per-app filter chips, and a dedicated deleted-WhatsApp-messages view.
 */
class NotificationsFragment : Fragment() {

    private lateinit var db: NotiDbHelper
    private lateinit var adapter: NotificationAdapter
    private lateinit var listView: ListView
    private lateinit var emptyState: View
    private lateinit var chipRow: LinearLayout
    private lateinit var deletedNote: TextView
    private lateinit var searchBox: EditText

    private var selectedPackage: String? = null
    private var deletedMode: Boolean = false
    private var currentQuery: String = ""
    private var unreadOnly = false
    private var dateRange = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_notifications, container, false)
        db = NotiDbHelper.getInstance(activity)
        unreadOnly = savedInstanceState?.getBoolean("unread_only") ?: false
        dateRange = savedInstanceState?.getInt("date_range") ?: 0
        selectedPackage = savedInstanceState?.getString("selected_package")
        deletedMode = savedInstanceState?.getBoolean("deleted_mode") ?: false
        currentQuery = savedInstanceState?.getString("query").orEmpty()

        listView = view.findViewById(R.id.notification_list)
        emptyState = view.findViewById(R.id.empty_state)
        chipRow = view.findViewById(R.id.chip_row)
        deletedNote = view.findViewById(R.id.deleted_note)
        searchBox = view.findViewById(R.id.search_box)
        searchBox.setText(currentQuery)
        view.findViewById<CheckBox>(R.id.filter_unread).isChecked = unreadOnly
        view.findViewById<Spinner>(R.id.date_range_filter).setSelection(dateRange)
        view.findViewById<CheckBox>(R.id.filter_unread).setOnCheckedChangeListener { _, checked ->
            unreadOnly = checked
            reload()
        }
        view.findViewById<Spinner>(R.id.date_range_filter).onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}
                override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                    dateRange = position
                    reload()
                }
            }

        adapter = NotificationAdapter(
            activity,
            emptyList(),
            onItemClick = { n ->
                db.markRead(n.id)
                openDetail(n.id)
                reload()
            },
            onDelete = { n -> confirmDelete(n) }
        )
        listView.adapter = adapter
        listView.setOnItemLongClickListener { _, _, position, _ ->
            val n = adapter.getItem(position) as SavedNotification
            showItemMenu(n)
            true
        }

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentQuery = s?.toString()?.trim().orEmpty()
                view.findViewById<ImageButton>(R.id.btn_clear_search).visibility =
                    if (currentQuery.isEmpty()) View.GONE else View.VISIBLE
                reload()
            }
        })
        view.findViewById<ImageButton>(R.id.btn_clear_search).visibility =
            if (currentQuery.isEmpty()) View.GONE else View.VISIBLE
        view.findViewById<ImageButton>(R.id.btn_clear_search).setOnClickListener {
            searchBox.setText("")
        }

        view.findViewById<ImageButton>(R.id.btn_menu).setOnClickListener { anchor ->
            showOverflowMenu(anchor)
        }
        return view
    }

    override fun onResume() {
        super.onResume()
        buildChips()
        reload()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("unread_only", unreadOnly)
        outState.putInt("date_range", dateRange)
        outState.putString("selected_package", selectedPackage)
        outState.putBoolean("deleted_mode", deletedMode)
        outState.putString("query", currentQuery)
        super.onSaveInstanceState(outState)
    }

    fun reload() {
        if (!::db.isInitialized) return
        val now = System.currentTimeMillis()
        val start = when (dateRange) {
            1 -> Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            2 -> now - 7L * 24 * 60 * 60 * 1000
            3 -> now - 30L * 24 * 60 * 60 * 1000
            else -> null
        }
        val raw = db.getNotifications(
            packageFilter = selectedPackage,
            query = currentQuery.ifBlank { null },
            unreadOnly = unreadOnly,
            fromMs = start,
            toMs = if (dateRange == 1) now + 1 else null
        )
        val items = if (deletedMode) {
            raw.filter { it.packageName in Constants.WHATSAPP_PACKAGES }
        } else raw
        adapter.update(items)
        val empty = items.isEmpty()
        listView.visibility = if (empty) View.GONE else View.VISIBLE
        emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        deletedNote.visibility = if (deletedMode) View.VISIBLE else View.GONE
        (activity as? com.taqijafri.notisave.MainActivity)?.refreshBadge()
    }

    /** Chips: All + every app with saved notifications + deleted-WhatsApp view. */
    private fun buildChips() {
        chipRow.removeAllViews()
        val activity = activity ?: return
        addChip(getString(R.string.filter_all), isSelected = selectedPackage == null && !deletedMode) {
            selectedPackage = null
            deletedMode = false
            buildChips()
            reload()
        }
        for ((pkg, name) in db.getAppsWithNotifications()) {
            addChip(name, isSelected = selectedPackage == pkg && !deletedMode) {
                selectedPackage = pkg
                deletedMode = false
                buildChips()
                reload()
            }
        }
        addChip(getString(R.string.filter_deleted_whatsapp), isSelected = deletedMode) {
            selectedPackage = null
            deletedMode = true
            buildChips()
            reload()
        }
    }

    private fun addChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
        val chip = TextView(activity).apply {
            text = label
            textSize = 13f
            setPadding(28, 16, 28, 16)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = 16
            layoutParams = lp
            if (isSelected) {
                setBackgroundResource(R.drawable.bg_chip_selected)
                setTextColor(Color.WHITE)
            } else {
                setBackgroundResource(R.drawable.bg_chip)
                setTextColor(resources.getColor(android.R.color.black))
            }
            setOnClickListener { onClick() }
        }
        // Re-resolve text color from the theme so dark mode stays readable.
        if (!isSelected) {
            val ta = activity.obtainStyledAttributes(intArrayOf(R.attr.textPrimary))
            chip.setTextColor(ta.getColor(0, Color.BLACK))
            ta.recycle()
        }
        chipRow.addView(chip)
    }

    private fun showOverflowMenu(anchor: View) {
        val popup = PopupMenu(activity, anchor)
        popup.menu.add(0, 1, 0, getString(R.string.menu_mark_all_read))
        popup.menu.add(0, 2, 0, getString(R.string.menu_clear_all))
        popup.menu.add(0, 3, 0, getString(R.string.menu_refresh))
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    db.markAllRead()
                    reload()
                    Toast.makeText(activity, R.string.marked_read_toast, Toast.LENGTH_SHORT).show()
                }
                2 -> confirmClearAll()
                3 -> {
                    buildChips()
                    reload()
                }
            }
            true
        }
        popup.show()
    }

    private fun showItemMenu(n: SavedNotification) {
        AlertDialog.Builder(activity)
            .setTitle(if (n.title.isNotBlank()) n.title else n.appName)
            .setItems(
                arrayOf(
                    getString(R.string.detail_title),
                    getString(R.string.menu_mark_all_read),
                    getString(R.string.btn_delete)
                )
            ) { _, which ->
                when (which) {
                    0 -> {
                        db.markRead(n.id)
                        openDetail(n.id)
                        reload()
                    }
                    1 -> {
                        db.markRead(n.id)
                        reload()
                    }
                    2 -> confirmDelete(n)
                }
            }
            .show()
    }

    private fun confirmDelete(n: SavedNotification) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.confirm_delete_title)
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                db.delete(n.id)
                reload()
                Toast.makeText(activity, R.string.deleted_toast, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun confirmClearAll() {
        AlertDialog.Builder(activity)
            .setTitle(R.string.confirm_clear_title)
            .setMessage(R.string.confirm_clear_msg)
            .setPositiveButton(R.string.btn_clear) { _, _ ->
                db.deleteAll()
                buildChips()
                reload()
                Toast.makeText(activity, R.string.cleared_toast, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun openDetail(id: Long) {
        val intent = Intent(activity, DetailActivity::class.java)
        intent.putExtra(DetailActivity.EXTRA_ID, id)
        startActivity(intent)
    }
}
