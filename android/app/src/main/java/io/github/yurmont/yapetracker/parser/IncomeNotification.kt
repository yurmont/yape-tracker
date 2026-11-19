package io.github.yurmont.yapetracker.parser

import io.github.yurmont.yapetracker.core.Money

/** An incoming payment read from a Yape notification. */
data class Income(val text: String, val amountCents: Long, val person: String?)

/**
 * Reads Yape's "… te envió un pago por S/ X" notifications. Pure Kotlin.
 *
 * A notification counts as an income only when it has an amount AND an income phrase, so promos such
 * as "Gana hasta S/ 50 …" are skipped.
 */
object IncomeNotification {

    private val AMOUNT = Regex("""S\s*/\s*\.?\s*(\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d+(?:[.,]\d{1,2})?)""", RegexOption.IGNORE_CASE)
    private val INCOME_PHRASE = Regex("""\bte\s+(?:envio|yapeo|pago|ha\s+enviado|transfirio)\b|\brecibiste\b""")
    private val PERSON = Regex("""([\p{L}][\p{L}.' ]*?)\s+te\s+(?:envi|yape|pag|ha\s+envi|transfiri)""", RegexOption.IGNORE_CASE)

    fun parse(title: String?, text: String?): Income? {
        val full = listOfNotNull(title?.trim(), text?.trim())
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .replace(Regex("""\s+"""), " ")
        if (!INCOME_PHRASE.containsMatchIn(ReceiptParser.fold(full))) return null
        val cents = AMOUNT.find(full)?.let { Money.parseCents(it.groupValues[1]) } ?: return null
        val person = PERSON.find(text ?: full)?.groupValues?.get(1)?.trim()
            ?.removePrefix("Yape").orEmpty().trim().ifEmpty { null }
        return Income(full, cents, person)
    }
}
