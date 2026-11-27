package com.taqijafri.notisave.ui

import android.app.Fragment
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import com.taqijafri.notisave.R
import com.taqijafri.notisave.data.AppEntry
import com.taqijafri.notisave.data.NotiDbHelper

/** Per-app save toggles: enable or disable notification saving per app. */
class AppsFragment : Fragment() {

    private lateinit var db: NotiDbHelper
    private lateinit var adapter: AppAdapter
    private var allApps: List<AppEntry> = emptyList()
    private var currentQuery: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_apps, container, false)
        db = NotiDbHelper.getInstance(activity)

        val listView = view.findViewById<ListView>(R.id.apps_list)
        adapter = AppAdapter(activity, emptyList()) { entry, enabled ->
            db.setAppEnabled(entry.packageName, enabled)
            allApps = allApps.map {
                if (it.packageName == entry.packageName) it.copy(enabled = enabled) else it
            }
            updateSectionLabel()
        }
        listView.adapter = adapter

        view.findViewById<EditText>(R.id.apps_search_box)
            .addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    currentQuery = s?.toString()?.trim().orEmpty()
                    applyFilter()
                }
            })
        return view
    }

    override fun onResume() {
        super.onResume()
        loadApps()
    }

    private fun loadApps() {
        val pm = activity.packageManager
        val installed = pm.getInstalledApplications(0)
        val savedPackages = db.getAppsWithNotifications().map { it.first }.toSet()

        val entries = ArrayList<AppEntry>()
        for (info in installed) {
            val pkg = info.packageName
            if (pkg == activity.packageName) continue
            val hasLauncher = pm.getLaunchIntentForPackage(pkg) != null
            val hasSaved = pkg in savedPackages
            if (!hasLauncher && !hasSaved) continue
            val name = pm.getApplicationLabel(info).toString()
            entries.add(
                AppEntry(
                    packageName = pkg,
                    appName = name,
                    savedCount = db.getCountByPackage(pkg),
                    enabled = db.isAppEnabled(pkg)
                )
            )
        }
        // Apps with saved notifications first, then alphabetical.
        entries.sortWith(
            compareByDescending<AppEntry> { it.savedCount > 0 }
                .thenBy { it.appName.lowercase() }
        )
        allApps = entries
        applyFilter()
    }

    private fun applyFilter() {
        val view = view ?: return
        val filtered = if (currentQuery.isBlank()) {
            allApps
        } else {
            allApps.filter {
                it.appName.contains(currentQuery, ignoreCase = true) ||
                    it.packageName.contains(currentQuery, ignoreCase = true)
            }
        }
        adapter.update(filtered)
        updateSectionLabel()
        view.findViewById<TextView>(R.id.apps_empty).visibility =
            if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateSectionLabel() {
        val label = view?.findViewById<TextView>(R.id.apps_section_label) ?: return
        val savingCount = allApps.count { it.enabled }
        label.text = getString(R.string.apps_saving_from, savingCount)
    }
}
