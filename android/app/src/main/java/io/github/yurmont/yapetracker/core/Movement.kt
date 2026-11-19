package io.github.yurmont.yapetracker.core

import java.math.BigDecimal
import java.math.RoundingMode

enum class MovementType(val label: String) {
    GASTO("Gasto"),
    INGRESO("Ingreso"),
}

/** One Yape payment stored on the phone. Amounts are in céntimos to avoid rounding errors. */
data class Movement(
    val id: Long = 0,
    val type: MovementType,
    val amountCents: Long,
    val person: String?,
    /** Date and time as Yape shows it, e.g. "19 nov. 2026 01:31 p. m." */
    val yapeDate: String?,
    val operation: String?,
    val concepto: String?,
    /** The receipt summary or the notification text, kept as-is. */
    val rawText: String,
    /** When it was saved in the app, epoch millis. */
    val createdAt: Long,
) {
    /** Negative for expenses, positive for incomes, so a column of these sums to the balance. */
    val signedCents: Long get() = if (type == MovementType.GASTO) -amountCents else amountCents
}

object Money {

    /** "0.40" → 40, "1,250.50" → 125050, "12,50" → 1250. Null when it isn't a positive amount. */
    fun parseCents(text: String): Long? {
        val s = text.trim().removePrefix("S/").trim()
        val normalized = if ('.' in s) s.replace(",", "") else s.replace(',', '.')
        return try {
            val cents = BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
            cents.takeIf { it > 0 }
        } catch (e: NumberFormatException) {
            null
        } catch (e: ArithmeticException) {
            null
        }
    }

    /** 125050 → "1,250.50", -40 → "-0.40" */
    fun format(cents: Long): String {
        val abs = BigDecimal.valueOf(Math.abs(cents), 2)
        val text = String.format(java.util.Locale.US, "%,.2f", abs)
        return if (cents < 0) "-$text" else text
    }
}
