package io.github.yurmont.yapetracker

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import io.github.yurmont.yapetracker.core.Movement
import io.github.yurmont.yapetracker.core.MovementType
import io.github.yurmont.yapetracker.parser.IncomeNotification

/** Saves incoming Yape payments from their notifications. Promos and other notifications are ignored. */
class YapeNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in Config.YAPE_PACKAGES) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()
        val income = IncomeNotification.parse(title, text) ?: return

        val now = System.currentTimeMillis()
        val db = MovementDb.get(this)
        // Yape may post or update the same notification more than once.
        if (db.recentIncomeExists(income.text, now - Config.INCOME_DEDUP_WINDOW_MS)) return

        db.insert(
            Movement(
                type = MovementType.INGRESO,
                amountCents = income.amountCents,
                person = income.person,
                yapeDate = null,
                operation = null,
                concepto = null,
                rawText = income.text,
                createdAt = now,
            ),
        )
    }
}
