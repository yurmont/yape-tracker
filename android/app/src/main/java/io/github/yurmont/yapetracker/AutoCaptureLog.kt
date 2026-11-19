package io.github.yurmont.yapetracker

import android.content.Context

/**
 * Remembers the last time automatic capture failed to register a "¡Yapeaste!", so the main screen
 * can tell the user to use "Compartir" for that payment. A later successful capture clears it.
 */
class AutoCaptureLog(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("auto_capture", Context.MODE_PRIVATE)

    /** The last problem, or null when the last receipt seen was registered. */
    val problem: String? get() = prefs.getString("problem", null)
    val problemAt: Long get() = prefs.getLong("problem_at", 0L)

    fun problem(message: String) =
        prefs.edit().putString("problem", message).putLong("problem_at", System.currentTimeMillis()).apply()

    fun ok() = prefs.edit().remove("problem").remove("problem_at").apply()
}
