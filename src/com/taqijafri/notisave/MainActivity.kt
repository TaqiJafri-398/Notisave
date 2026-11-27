package com.taqijafri.notisave

import android.app.Activity
import android.app.AlertDialog
import android.app.Fragment
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.taqijafri.notisave.data.NotiDbHelper
import com.taqijafri.notisave.data.Prefs
import com.taqijafri.notisave.ui.AppsFragment
import com.taqijafri.notisave.ui.HomeFragment
import com.taqijafri.notisave.ui.NotificationsFragment
import com.taqijafri.notisave.ui.SettingsFragment
import com.taqijafri.notisave.ui.StatusesFragment
import com.taqijafri.notisave.util.ListenerHelper
import com.taqijafri.notisave.util.ThemeHelper
import com.taqijafri.notisave.util.AppLockGate

/**
 * Single-activity host with bottom navigation:
 * Home, Notifications, Apps, Settings.
 */
class MainActivity : Activity() {

    companion object {
        const val TAB_HOME = 0
        const val TAB_NOTIFICATIONS = 1
        const val TAB_STATUSES = 2
        const val TAB_APPS = 3
        const val TAB_SETTINGS = 4
        private const val TAG_HOME = "tab_home"
        private const val TAG_NOTIFICATIONS = "tab_notifications"
        private const val TAG_STATUSES = "tab_statuses"
        private const val TAG_APPS = "tab_apps"
        private const val TAG_SETTINGS = "tab_settings"
    }

    private lateinit var prefs: Prefs
    private lateinit var db: NotiDbHelper

    private lateinit var homeFragment: HomeFragment
    private lateinit var notificationsFragment: NotificationsFragment
    private lateinit var statusesFragment: StatusesFragment
    private lateinit var appsFragment: AppsFragment
    private lateinit var settingsFragment: SettingsFragment

    private var currentTab = -1

    private lateinit var navIcons: Array<ImageView>
    private lateinit var navLabels: Array<TextView>
    private lateinit var navItems: Array<LinearLayout>
    private lateinit var badge: TextView
    private var authInProgress = false
    private var lockContentView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        homeFragment = fragment(TAG_HOME) { HomeFragment() }
        notificationsFragment = fragment(TAG_NOTIFICATIONS) { NotificationsFragment() }
        statusesFragment = fragment(TAG_STATUSES) { StatusesFragment() }
        appsFragment = fragment(TAG_APPS) { AppsFragment() }
        settingsFragment = fragment(TAG_SETTINGS) { SettingsFragment() }
        if (Prefs(this).appLockEnabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        lockContentView = findViewById(android.R.id.content)
        if (Prefs(this).appLockEnabled) lockContentView?.visibility = View.INVISIBLE

        prefs = Prefs(this)
        db = NotiDbHelper.getInstance(this)

        navIcons = arrayOf(
            findViewById(R.id.nav_home_icon),
            findViewById(R.id.nav_notifications_icon),
            findViewById(R.id.nav_statuses_icon),
            findViewById(R.id.nav_apps_icon),
            findViewById(R.id.nav_settings_icon)
        )
        navLabels = arrayOf(
            findViewById(R.id.nav_home_label),
            findViewById(R.id.nav_notifications_label),
            findViewById(R.id.nav_statuses_label),
            findViewById(R.id.nav_apps_label),
            findViewById(R.id.nav_settings_label)
        )
        navItems = arrayOf(
            findViewById(R.id.nav_home),
            findViewById(R.id.nav_notifications),
            findViewById(R.id.nav_statuses),
            findViewById(R.id.nav_apps),
            findViewById(R.id.nav_settings)
        )
        badge = findViewById(R.id.nav_badge)

        navItems.forEachIndexed { index, item ->
            item.setOnClickListener { selectTab(index) }
        }

        selectTab(savedInstanceState?.getInt("tab", TAB_HOME) ?: TAB_HOME)
        showWelcomeIfFirstRun()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", currentTab)
    }

    override fun onResume() {
        super.onResume()
        if (Prefs(this).appLockEnabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (::prefs.isInitialized && AppLockGate.requiresUnlock(this)) {
            lockContentView?.visibility = View.INVISIBLE
            if (!authInProgress) authenticateForLock()
        } else {
            lockContentView?.visibility = View.VISIBLE
        }
        refreshBadge()
    }

    override fun onPause() {
        super.onPause()
        if (::prefs.isInitialized && prefs.appLockEnabled) lockContentView?.visibility = View.INVISIBLE
    }

    private fun authenticateForLock() {
        if (authInProgress || isFinishing) return
        authInProgress = true
        AppLockGate.authenticateWithRetry(this) {
            authInProgress = false
            lockContentView?.visibility = View.VISIBLE
        }
    }

    fun selectTab(index: Int) {
        if (index == currentTab) return
        currentTab = index
        val tag = when (index) {
            TAB_NOTIFICATIONS -> TAG_NOTIFICATIONS
            TAB_STATUSES -> TAG_STATUSES
            TAB_APPS -> TAG_APPS
            TAB_SETTINGS -> TAG_SETTINGS
            else -> TAG_HOME
        }
        val fragment: Fragment = when (index) {
            TAB_NOTIFICATIONS -> notificationsFragment
            TAB_STATUSES -> statusesFragment
            TAB_APPS -> appsFragment
            TAB_SETTINGS -> settingsFragment
            else -> homeFragment
        }
        fragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment, tag)
            .commit()
        updateNavAppearance()
        refreshBadge()
    }

    /** Called by Settings when the theme changes. */
    fun recreateWithTheme() {
        recreate()
    }

    fun refreshBadge() {
        if (!::db.isInitialized) return
        val unread = db.getUnreadCount()
        if (unread > 0) {
            badge.visibility = View.VISIBLE
            badge.text = if (unread > 99) "99+" else unread.toString()
        } else {
            badge.visibility = View.GONE
        }
    }

    private fun updateNavAppearance() {
        val selectedColor = Color.parseColor("#4F46E5")
        val unselectedColor = Color.parseColor("#9CA3AF")
        navIcons.forEachIndexed { i, icon ->
            icon.imageTintList = ColorStateList.valueOf(
                if (i == currentTab) selectedColor else unselectedColor
            )
        }
        navLabels.forEachIndexed { i, label ->
            label.setTextColor(if (i == currentTab) selectedColor else unselectedColor)
        }
    }

    private fun <T : Fragment> fragment(tag: String, create: () -> T): T =
        (fragmentManager.findFragmentByTag(tag) as? T) ?: create()

    private fun showWelcomeIfFirstRun() {
        if (prefs.privacyShown) return
        AlertDialog.Builder(this)
            .setTitle(R.string.welcome_title)
            .setMessage(R.string.welcome_msg)
            .setPositiveButton(R.string.btn_continue) { _, _ ->
                prefs.privacyShown = true
                ListenerHelper.openListenerSettings(this)
            }
            .setNegativeButton(R.string.btn_not_now) { _, _ ->
                prefs.privacyShown = true
            }
            .setCancelable(false)
            .show()
    }
}
