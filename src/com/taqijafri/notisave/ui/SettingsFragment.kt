package com.taqijafri.notisave.ui

import android.app.AlertDialog
import android.app.Fragment
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Switch
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import android.widget.EditText
import android.app.Activity
import com.taqijafri.notisave.MainActivity
import com.taqijafri.notisave.R
import com.taqijafri.notisave.data.NotiDbHelper
import com.taqijafri.notisave.data.Prefs
import com.taqijafri.notisave.util.Constants
import com.taqijafri.notisave.util.ListenerHelper
import com.taqijafri.notisave.util.ThemeHelper
import com.taqijafri.notisave.util.NotificationBackup
import com.taqijafri.notisave.util.AppLockGate

/** Settings: theme, retention, permissions, privacy, about developer. */
class SettingsFragment : Fragment() {
    private val createBackupRequest = 4101
    private val restoreBackupRequest = 4102
    private var pendingPassword: CharArray? = null

    private lateinit var db: NotiDbHelper
    private lateinit var prefs: Prefs

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        db = NotiDbHelper.getInstance(activity)
        prefs = Prefs(activity)

        view.findViewById<Switch>(R.id.switch_capture_pause).apply {
            isChecked = prefs.capturePaused
            setOnCheckedChangeListener { _, enabled -> prefs.capturePaused = enabled }
        }
        view.findViewById<Switch>(R.id.switch_redact_previews).apply {
            isChecked = prefs.redactPreviews
            setOnCheckedChangeListener { _, enabled ->
                prefs.redactPreviews = enabled
                (activity as? MainActivity)?.recreate()
            }
        }
        view.findViewById<Switch>(R.id.switch_app_lock).apply {
            isChecked = prefs.appLockEnabled
            setOnCheckedChangeListener { _, enabled ->
                if (enabled && (android.os.Build.VERSION.SDK_INT < 29 ||
                    !(activity.getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isDeviceSecure)) {
                    isChecked = false
                    val message = if (android.os.Build.VERSION.SDK_INT < 29) {
                        R.string.app_lock_unavailable
                    } else {
                        R.string.app_lock_setup_device
                    }
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                } else {
                    AppLockGate.reset(activity)
                    prefs.appLockEnabled = enabled
                    (activity as? MainActivity)?.recreate()
                }
            }
        }
        view.findViewById<Button>(R.id.btn_backup_export).setOnClickListener {
            askBackupPassword { password ->
                pendingPassword = password
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type = "application/octet-stream"
                    putExtra(Intent.EXTRA_TITLE, "notisave-backup.nsb")
                }, createBackupRequest)
            }
        }
        view.findViewById<Button>(R.id.btn_backup_import).setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "application/octet-stream"
            }, restoreBackupRequest)
        }

        // --- theme ---
        val themeGroup = view.findViewById<RadioGroup>(R.id.theme_group)
        when (prefs.themeMode) {
            Prefs.THEME_LIGHT -> themeGroup.check(R.id.theme_light)
            Prefs.THEME_DARK -> themeGroup.check(R.id.theme_dark)
            else -> themeGroup.check(R.id.theme_system)
        }
        themeGroup.setOnCheckedChangeListener { _, checkedId ->
            prefs.themeMode = when (checkedId) {
                R.id.theme_light -> Prefs.THEME_LIGHT
                R.id.theme_dark -> Prefs.THEME_DARK
                else -> Prefs.THEME_SYSTEM
            }
            (activity as? MainActivity)?.recreateWithTheme()
        }

        // --- retention ---
        val retentionGroup = view.findViewById<RadioGroup>(R.id.retention_group)
        when (prefs.retentionDays) {
            Prefs.RETENTION_30 -> retentionGroup.check(R.id.retention_30)
            Prefs.RETENTION_60 -> retentionGroup.check(R.id.retention_60)
            Prefs.RETENTION_UNLIMITED -> retentionGroup.check(R.id.retention_unlimited)
            else -> retentionGroup.check(R.id.retention_90)
        }
        retentionGroup.setOnCheckedChangeListener { _, checkedId ->
            prefs.retentionDays = when (checkedId) {
                R.id.retention_30 -> Prefs.RETENTION_30
                R.id.retention_60 -> Prefs.RETENTION_60
                R.id.retention_unlimited -> Prefs.RETENTION_UNLIMITED
                else -> Prefs.RETENTION_90
            }
        }

        // --- storage ---
        val total = db.getTotalCount()
        val size = ThemeHelper.formatSize(db.getDbSizeBytes(activity))
        view.findViewById<TextView>(R.id.storage_size).text = "$size • $total"
        view.findViewById<Button>(R.id.btn_prune).setOnClickListener {
            val days = prefs.retentionDays
            if (days > 0) {
                AlertDialog.Builder(activity)
                    .setTitle(R.string.prune_confirm_title)
                    .setMessage(getString(R.string.prune_confirm_message, days))
                    .setPositiveButton(R.string.btn_delete) { _, _ ->
                        val cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
                        db.deleteOlderThan(cutoff)
                        Toast.makeText(activity, R.string.pruned_toast, Toast.LENGTH_SHORT).show()
                        val newTotal = db.getTotalCount()
                        val newSize = ThemeHelper.formatSize(db.getDbSizeBytes(activity))
                        view.findViewById<TextView>(R.id.storage_size).text = "$newSize • $newTotal"
                    }
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show()
            }
        }

        // --- permissions ---
        view.findViewById<TextView>(R.id.access_status_text).text =
            getString(
                if (ListenerHelper.isListenerEnabled(activity)) R.string.status_enabled
                else R.string.status_disabled
            )
        view.findViewById<Button>(R.id.btn_access_settings).setOnClickListener {
            ListenerHelper.openListenerSettings(activity)
        }
        view.findViewById<Button>(R.id.btn_restart_listener).setOnClickListener {
            ListenerHelper.restartListener(activity)
            Toast.makeText(activity, R.string.listener_restarted, Toast.LENGTH_SHORT).show()
        }

        // --- privacy ---
        view.findViewById<Button>(R.id.btn_privacy).setOnClickListener {
            AlertDialog.Builder(activity)
                .setTitle(R.string.privacy_dialog_title)
                .setMessage(R.string.privacy_full)
                .setPositiveButton(R.string.btn_understand, null)
                .show()
        }

        // --- about developer / WhatsApp ---
        view.findViewById<Button>(R.id.btn_whatsapp).setOnClickListener {
            try {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(Constants.WHATSAPP_CONTACT_URL)
                )
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(activity, R.string.whatsapp_open_failed, Toast.LENGTH_SHORT).show()
            }
        }
        return view
    }

    override fun onResume() {
        super.onResume()
        val view = view ?: return
        view.findViewById<TextView>(R.id.access_status_text).text =
            getString(
                if (ListenerHelper.isListenerEnabled(activity)) R.string.status_enabled
                else R.string.status_disabled
            )
    }

    private fun askBackupPassword(onPassword: (CharArray) -> Unit) {
        val input = EditText(activity).apply {
            inputType = 129
            hint = getString(R.string.backup_password_hint)
        }
        AlertDialog.Builder(activity).setTitle(R.string.backup_password_title)
            .setMessage(R.string.backup_password_message).setView(input)
            .setPositiveButton(R.string.btn_continue) { _, _ ->
                val password = input.text.toString().toCharArray()
                if (password.size < 8) Toast.makeText(activity, R.string.backup_password_short, Toast.LENGTH_LONG).show()
                else onPassword(password)
            }.setNegativeButton(R.string.btn_cancel, null).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == createBackupRequest) {
            if (resultCode != Activity.RESULT_OK || data?.data == null) {
                pendingPassword?.fill('\u0000')
                pendingPassword = null
                return
            }
            val password = pendingPassword ?: return
            pendingPassword = null
            val uri = data.data!!
            runBackupTask(password, { getString(R.string.backup_export_done) }) { appContext ->
                NotificationBackup.export(appContext, uri, password)
                null
            }
        } else if (requestCode == restoreBackupRequest) {
            if (resultCode != Activity.RESULT_OK || data?.data == null) return
            val uri = data.data!!
            askBackupPassword { password ->
                runBackupTask(password, { count -> getString(R.string.backup_import_done, count) }) { appContext ->
                    NotificationBackup.import(appContext, uri, password)
                }
            }
        }
    }

    private fun runBackupTask(
        password: CharArray,
        successMessage: (Int?) -> String,
        work: (android.content.Context) -> Int?
    ) {
        val owner = activity ?: run { password.fill('\u0000'); return }
        val appContext = owner.applicationContext
        Toast.makeText(owner, R.string.backup_working, Toast.LENGTH_SHORT).show()
        Thread {
            val result = try {
                Result.success(work(appContext))
            } catch (error: Exception) {
                Result.failure<Int?>(error)
            } finally {
                password.fill('\u0000')
            }
            owner.runOnUiThread {
                if (!isAdded || activity !== owner) return@runOnUiThread
                val message = result.fold(
                    { successMessage(it) },
                    { owner.getString(R.string.backup_failed, it.message ?: "") }
                )
                Toast.makeText(owner, message, Toast.LENGTH_LONG).show()
                (owner as? MainActivity)?.refreshBadge()
            }
        }.start()
    }
}
