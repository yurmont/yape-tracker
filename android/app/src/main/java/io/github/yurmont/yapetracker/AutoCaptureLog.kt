package io.github.yurmont.yapetracker

import android.content.Context

/**
 * What the automatic capture has seen, shown on the main screen. While this feature is being tested
 * it tells us whether Yape lets the service see its screen at all.
 */
class AutoCaptureLog(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("auto_capture", Context.MODE_PRIVATE)

    val events: Int get() = prefs.getInt("events", 0)
    val captures: Int get() = prefs.getInt("captures", 0)
    val saved: Int get() = prefs.getInt("saved", 0)
    val lastMessage: String? get() = prefs.getString("last_message", null)
    val lastAt: Long get() = prefs.getLong("last_at", 0L)

    fun countEvent() = prefs.edit().putInt("events", events + 1).apply()

    fun countCapture() = prefs.edit().putInt("captures", captures + 1).apply()

    fun countSaved() = prefs.edit().putInt("saved", saved + 1).apply()

    fun note(message: String) =
        prefs.edit().putString("last_message", message).putLong("last_at", System.currentTimeMillis()).apply()
}
