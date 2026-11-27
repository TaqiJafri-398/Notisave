package com.taqijafri.notisave.util

import android.app.Activity
import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import com.taqijafri.notisave.R
import com.taqijafri.notisave.data.Prefs

object AppLockGate {
    private var lastUnlockAt: Long? = null

    fun requiresUnlock(context: Context): Boolean {
        val prefs = Prefs(context)
        if (!prefs.appLockEnabled) {
            reset(context)
            return false
        }
        val bootCount = Settings.Global.getInt(
            context.contentResolver, Settings.Global.BOOT_COUNT, -1
        )
        val unlockedAt = if (bootCount >= 0 && prefs.lastUnlockBootCount == bootCount) {
            prefs.lastUnlockAt
        } else {
            lastUnlockAt ?: return true
        }
        if (unlockedAt == 0L) return true
        val elapsed = SystemClock.elapsedRealtime() - unlockedAt
        return elapsed !in 0..30_000L
    }

    fun reset(context: Context) {
        lastUnlockAt = null
        Prefs(context).apply {
            lastUnlockAt = 0L
            lastUnlockBootCount = -1
        }
    }

    fun authenticate(activity: Activity, onSuccess: () -> Unit, onEnded: () -> Unit = {}) {
        if (Build.VERSION.SDK_INT < 29 ||
            !(activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure) {
            val message = if (Build.VERSION.SDK_INT < 29) {
                R.string.app_lock_unavailable
            } else {
                R.string.app_lock_setup_device
            }
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
            Prefs(activity).appLockEnabled = false
            reset(activity)
            onSuccess()
            activity.recreate()
            return
        }
        val builder = android.hardware.biometrics.BiometricPrompt.Builder(activity)
            .setTitle(activity.getString(R.string.app_lock_title))
            .setDescription(activity.getString(R.string.app_lock_message))
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setAllowedAuthenticators(
                android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        } else {
            builder.setDeviceCredentialAllowed(true)
        }
        builder.build()
            .authenticate(android.os.CancellationSignal(), activity.mainExecutor,
                object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) {
                        val unlockedAt = SystemClock.elapsedRealtime()
                        val bootCount = Settings.Global.getInt(
                            activity.contentResolver, Settings.Global.BOOT_COUNT, -1
                        )
                        lastUnlockAt = unlockedAt
                        if (bootCount >= 0) {
                            Prefs(activity).apply {
                                lastUnlockAt = unlockedAt
                                lastUnlockBootCount = bootCount
                            }
                        }
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        if (errorCode != 5 && errorCode != 10 && errorCode != 13) {
                            Toast.makeText(activity, errString ?: activity.getString(R.string.app_lock_unavailable), Toast.LENGTH_LONG).show()
                        }
                        onEnded()
                    }
                })
    }

    fun showRetryDialog(activity: Activity, onRetry: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) return
        AlertDialog.Builder(activity)
            .setTitle(R.string.app_lock_title)
            .setMessage(R.string.app_lock_retry_message)
            .setPositiveButton(R.string.btn_retry) { _, _ -> onRetry() }
            .setNegativeButton(R.string.btn_cancel) { _, _ -> activity.finish() }
            .setOnCancelListener { activity.finish() }
            .show()
    }

    fun authenticateWithRetry(activity: Activity, onSuccess: () -> Unit) {
        authenticate(activity, onSuccess) {
            showRetryDialog(activity) { authenticateWithRetry(activity, onSuccess) }
        }
    }
}
