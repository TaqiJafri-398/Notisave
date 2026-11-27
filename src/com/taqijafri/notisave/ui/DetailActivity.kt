package com.taqijafri.notisave.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.taqijafri.notisave.R
import com.taqijafri.notisave.data.NotiDbHelper
import com.taqijafri.notisave.data.Prefs
import com.taqijafri.notisave.util.AppLockGate
import com.taqijafri.notisave.util.AppInfo
import com.taqijafri.notisave.util.ThemeHelper
import com.taqijafri.notisave.util.TimeUtils

/** Full view of one saved notification. */
class DetailActivity : Activity() {
    private var lockedContent: View? = null
    private var lockAuthInProgress = false

    companion object {
        const val EXTRA_ID = "notification_id"
    }

    private var notificationId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)
        if (Prefs(this).appLockEnabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        lockedContent = findViewById(android.R.id.content)
        if (AppLockGate.requiresUnlock(this)) lockedContent?.visibility = View.INVISIBLE

        notificationId = intent.getLongExtra(EXTRA_ID, -1)
        val db = NotiDbHelper.getInstance(this)
        val n = db.getById(notificationId)
        if (n == null) {
            finish()
            return
        }
        db.markRead(n.id)

        val icon = findViewById<ImageView>(R.id.detail_icon)
        val drawable = AppInfo.getAppIcon(packageManager, n.packageName)
        if (drawable != null) icon.setImageDrawable(drawable)
        else icon.setImageResource(android.R.drawable.sym_def_app_icon)

        findViewById<TextView>(R.id.detail_app_name).text = n.appName
        findViewById<TextView>(R.id.detail_time).text = TimeUtils.fullTime(n.postTime)
        findViewById<TextView>(R.id.detail_package).text = n.packageName
        val redact = Prefs(this).redactPreviews
        findViewById<TextView>(R.id.detail_title).text = if (redact) getString(R.string.preview_hidden)
            else if (n.title.isNotBlank()) n.title else n.appName
        findViewById<TextView>(R.id.detail_text).text = if (redact) getString(R.string.preview_hidden) else n.fullText

        findViewById<Button>(R.id.btn_detail_open).setOnClickListener {
            if (!AppInfo.openApp(this, n.packageName)) {
                Toast.makeText(this, R.string.app_not_installed, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.btn_detail_share).setOnClickListener {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject))
                putExtra(
                    Intent.EXTRA_TEXT,
                    if (redact) getString(R.string.preview_hidden)
                    else "${n.appName} — ${n.title}\n${n.fullText}\n${TimeUtils.fullTime(n.postTime)}"
                )
            }
            startActivity(Intent.createChooser(share, getString(R.string.btn_share)))
        }
        findViewById<Button>(R.id.btn_detail_delete).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.confirm_delete_title)
                .setPositiveButton(R.string.btn_delete) { _, _ ->
                    db.delete(n.id)
                    finish()
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (Prefs(this).appLockEnabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (AppLockGate.requiresUnlock(this)) {
            lockedContent?.visibility = View.INVISIBLE
            if (!lockAuthInProgress) authenticateForLock()
        } else lockedContent?.visibility = View.VISIBLE
    }

    override fun onPause() {
        super.onPause()
        if (Prefs(this).appLockEnabled) {
            lockedContent?.visibility = View.INVISIBLE
        }
    }

    private fun authenticateForLock() {
        if (lockAuthInProgress || isFinishing) return
        lockAuthInProgress = true
        AppLockGate.authenticateWithRetry(this) {
            lockAuthInProgress = false
            lockedContent?.visibility = View.VISIBLE
        }
    }
}
