package com.taqijafri.notisave.service

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.taqijafri.notisave.data.NotiDbHelper
import com.taqijafri.notisave.data.Prefs
import com.taqijafri.notisave.data.SavedNotification
import com.taqijafri.notisave.util.AppInfo
import com.taqijafri.notisave.util.Constants
import com.taqijafri.notisave.util.NotificationText
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Captures posted notifications and saves them to the local database.
 *
 * - Runs only when the user grants Notification Access in Android Settings.
 * - Event-driven: no polling, no wake locks — battery friendly.
 * - Only notifications that arrive while saving is on can be shown later.
 *   Nothing here bypasses encryption or reads private app databases.
 */
class NotiListenerService : NotificationListenerService() {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val dedupeWindowMs = 5_000L

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Housekeeping on (re)connect: drop history older than the retention setting.
        executor.execute { pruneOld() }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // Android rebinds automatically when the next notification arrives.
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (Prefs(this).capturePaused) return
        if (pkg in Constants.BLOCKED_PACKAGES) return

        val notification = sbn.notification ?: return
        // Skip persistent / ongoing notifications (music players, foreground services).
        val flags = notification.flags
        if ((flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        if ((flags and Notification.FLAG_FOREGROUND_SERVICE) != 0) return

        val extras = notification.extras ?: return
        val (title, body) = NotificationText.extract(extras)
        if (body.isBlank() && title.isBlank()) return

        val postTime = sbn.postTime
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()

        executor.execute {
            try {
                val db = NotiDbHelper.getInstance(this)
                if (!db.isAppEnabled(pkg)) return@execute
                if (db.isDuplicate(pkg, title, body, dedupeWindowMs)) return@execute
                val appName = AppInfo.getAppName(packageManager, pkg)
                db.insert(
                    SavedNotification(
                        packageName = pkg,
                        appName = appName,
                        title = title,
                        text = body,
                        bigText = bigText,
                        postTime = postTime
                    )
                )
            } catch (e: Exception) {
                // Never crash the listener on a bad notification.
            }
        }
    }

    private fun pruneOld() {
        try {
            val prefs = Prefs(this)
            val days = prefs.retentionDays
            if (days <= 0) return // unlimited
            val cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
            NotiDbHelper.getInstance(this).deleteOlderThan(cutoff)
        } catch (e: Exception) {
            // Housekeeping must never break the service.
        }
    }
}

/**
 * After a reboot (or an app update) Android does not always rebind the
 * listener immediately. Toggling the component forces a clean rebind.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            try {
                com.taqijafri.notisave.util.ListenerHelper.restartListener(context)
            } catch (e: Exception) {
                // Best effort only.
            }
        }
    }
}
