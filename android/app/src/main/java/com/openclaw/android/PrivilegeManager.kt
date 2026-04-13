package com.openclaw.android

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

class PrivilegeManager {
    private val handler = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<(Boolean) -> Unit>()

    private var expiresAtElapsedRealtime: Long? = null
    private var expiresAtEpochMillis: Long? = null

    private val revokeRunnable =
        Runnable {
            revoke()
        }

    @Synchronized
    fun isPrivileged(): Boolean {
        val expiresAt = expiresAtElapsedRealtime ?: return false
        if (SystemClock.elapsedRealtime() >= expiresAt) {
            revoke()
            return false
        }
        return true
    }

    @Synchronized
    fun grant(durationMs: Long) {
        val safeDurationMs = durationMs.coerceAtLeast(0L)
        val nowElapsed = SystemClock.elapsedRealtime()
        expiresAtElapsedRealtime = nowElapsed + safeDurationMs
        expiresAtEpochMillis = System.currentTimeMillis() + safeDurationMs
        handler.removeCallbacks(revokeRunnable)
        handler.postDelayed(revokeRunnable, safeDurationMs)
        notifyListeners(true)
    }

    @Synchronized
    fun revoke() {
        val wasPrivileged = expiresAtElapsedRealtime != null
        expiresAtElapsedRealtime = null
        expiresAtEpochMillis = null
        handler.removeCallbacks(revokeRunnable)
        if (wasPrivileged) {
            notifyListeners(false)
        }
    }

    @Synchronized
    fun requirePrivilege() {
        if (!isPrivileged()) {
            throw SecurityException("Advanced Mode is required for this action.")
        }
    }

    @Synchronized
    fun remainingMs(): Long {
        val expiresAt = expiresAtElapsedRealtime ?: return 0L
        val remaining = expiresAt - SystemClock.elapsedRealtime()
        if (remaining <= 0L) {
            revoke()
            return 0L
        }
        return remaining
    }

    @Synchronized
    fun expiresAtEpochMillis(): Long? {
        if (!isPrivileged()) return null
        return expiresAtEpochMillis
    }

    @Synchronized
    fun addListener(listener: (Boolean) -> Unit) {
        listeners += listener
    }

    @Synchronized
    fun removeListener(listener: (Boolean) -> Unit) {
        listeners -= listener
    }

    @Synchronized
    private fun notifyListeners(privileged: Boolean) {
        listeners.toList().forEach { listener ->
            listener(privileged)
        }
    }
}
