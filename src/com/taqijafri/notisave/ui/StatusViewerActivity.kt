package com.taqijafri.notisave.ui

import android.app.Activity
import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.MediaController
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import com.taqijafri.notisave.R
import com.taqijafri.notisave.util.StatusFile
import com.taqijafri.notisave.util.StatusHelper
import com.taqijafri.notisave.util.ThemeHelper
import com.taqijafri.notisave.util.TimeUtils
import com.taqijafri.notisave.util.AppLockGate
import com.taqijafri.notisave.data.Prefs

/** Full-screen viewer for one WhatsApp status, with a Save button. */
class StatusViewerActivity : Activity() {
    private var lockedContent: View? = null
    private var lockAuthInProgress = false
    private var videoPrepared = false

    companion object {
        const val EXTRA_URI = "uri"
        const val EXTRA_NAME = "name"
        const val EXTRA_IS_VIDEO = "is_video"
        const val EXTRA_IS_SAVED_COPY = "is_saved_copy"
        const val EXTRA_MODIFIED = "modified"
        const val EXTRA_SIZE = "size"
    }

    private lateinit var status: StatusFile

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_status_viewer)
        if (com.taqijafri.notisave.data.Prefs(this).appLockEnabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        lockedContent = findViewById(android.R.id.content)
        if (AppLockGate.requiresUnlock(this)) lockedContent?.visibility = View.INVISIBLE

        val uriString = intent.getStringExtra(EXTRA_URI) ?: run { finish(); return }
        status = StatusFile(
            uri = Uri.parse(uriString),
            name = intent.getStringExtra(EXTRA_NAME) ?: "",
            isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, false),
            lastModified = intent.getLongExtra(EXTRA_MODIFIED, 0L),
            size = intent.getLongExtra(EXTRA_SIZE, 0L),
            isSavedCopy = intent.getBooleanExtra(EXTRA_IS_SAVED_COPY, false)
        )

        val imageView = findViewById<ImageView>(R.id.viewer_image)
        val videoView = findViewById<VideoView>(R.id.viewer_video)

        if (status.isVideo) {
            videoView.visibility = View.VISIBLE
            videoView.setVideoURI(status.uri)
            val controller = MediaController(this)
            controller.setAnchorView(videoView)
            videoView.setMediaController(controller)
            videoView.setOnPreparedListener {
                videoPrepared = true
                if (!AppLockGate.requiresUnlock(this)) it.start()
            }
        } else {
            imageView.visibility = View.VISIBLE
            val bitmap = StatusHelper.thumbnail(this, status, 1080)
            if (bitmap != null) imageView.setImageBitmap(bitmap)
            else imageView.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        findViewById<TextView>(R.id.viewer_name).text = status.name
        findViewById<TextView>(R.id.viewer_meta).text =
            "${ThemeHelper.formatSize(status.size)} • ${TimeUtils.fullTime(status.lastModified)}"

        val saveBtn = findViewById<Button>(R.id.viewer_save)
        val deleteBtn = findViewById<Button>(R.id.viewer_delete)
        updateButtons(saveBtn, deleteBtn)

        saveBtn.setOnClickListener {
            if (StatusHelper.isSaved(this, status)) {
                Toast.makeText(this, R.string.status_already_saved, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val saved = StatusHelper.saveStatus(this, status)
            if (saved != null) {
                Toast.makeText(this, R.string.status_saved_toast, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.status_save_failed, Toast.LENGTH_SHORT).show()
            }
            updateButtons(saveBtn, deleteBtn)
        }
        deleteBtn.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.confirm_delete_title)
                .setPositiveButton(R.string.btn_delete) { _, _ ->
                    if (StatusHelper.deleteSaved(this, status)) {
                        Toast.makeText(this, R.string.status_deleted_toast, Toast.LENGTH_SHORT)
                            .show()
                        finish()
                    }
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        }
    }

    private fun updateButtons(saveBtn: Button, deleteBtn: Button) {
        val saved = StatusHelper.isSaved(this, status)
        saveBtn.text = getString(if (saved) R.string.viewer_saved else R.string.btn_save)
        saveBtn.isEnabled = !saved
        saveBtn.alpha = if (saved) 0.6f else 1f
        deleteBtn.visibility = if (status.isSavedCopy) View.VISIBLE else View.GONE
    }

    override fun onPause() {
        super.onPause()
        findViewById<VideoView>(R.id.viewer_video).pause()
        if (Prefs(this).appLockEnabled) {
            lockedContent?.visibility = View.INVISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        if (Prefs(this).appLockEnabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (AppLockGate.requiresUnlock(this)) {
            lockedContent?.visibility = View.INVISIBLE
            if (!lockAuthInProgress) authenticateForLock()
        } else revealContent()
    }

    private fun revealContent() {
        lockedContent?.visibility = View.VISIBLE
        if (videoPrepared && status.isVideo && !AppLockGate.requiresUnlock(this)) {
            findViewById<VideoView>(R.id.viewer_video).start()
        }
    }

    private fun authenticateForLock() {
        if (lockAuthInProgress || isFinishing) return
        lockAuthInProgress = true
        AppLockGate.authenticateWithRetry(this) {
            lockAuthInProgress = false
            revealContent()
        }
    }
}
