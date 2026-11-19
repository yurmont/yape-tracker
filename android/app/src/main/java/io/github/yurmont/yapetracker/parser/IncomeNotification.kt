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

    // Yape masks surnames with "*" ("Ronald Yur*"), so names may contain it.
    private const val NAME_CHARS = """[\p{L}.'*]"""

    /** "Juan Per* te envió un pago …" */
    private val NAME_BEFORE_PHRASE = Regex(
        """(\p{L}$NAME_CHARS*(?:[ ]+$NAME_CHARS+){0,4}?)\s+te\s+(?:envi|yape|pag|ha\s+envi|transfiri)""",
        RegexOption.IGNORE_CASE,
    )

    /** "Recibiste un yapeo de S/ 0.40 de Juan Pérez" */
    // Case-sensitive on purpose: names start with a capital, so "de seguridad" is not taken as a name.
    private val NAME_AFTER_RECIBISTE = Regex(
        """[Rr]ecibiste\b.*?\bde\s+(?!S\s*/)(\p{Lu}$NAME_CHARS*(?:\s+\p{Lu}$NAME_CHARS*){0,4})""",
    )

    /** Words that are never part of the payer's name. */
    private val NOT_NAMES = setOf("yape", "yape!", "confirmacion", "de", "pago", "recibiste")

    fun parse(title: String?, text: String?): Income? {
        val full = listOfNotNull(title?.trim(), text?.trim())
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .replace(Regex("""\s+"""), " ")
        if (!INCOME_PHRASE.containsMatchIn(ReceiptParser.fold(full))) return null
        val cents = AMOUNT.find(full)?.let { Money.parseCents(it.groupValues[1]) } ?: return null
        val person = listOfNotNull(text, full).firstNotNullOfOrNull(::findPerson)
        return Income(full, cents, person)
    }

    /** Re-reads the payer from a stored notification text (title and text already joined). */
    fun personFrom(storedText: String): String? = findPerson(storedText)

    private fun findPerson(s: String): String? {
        val raw = NAME_BEFORE_PHRASE.find(s)?.groupValues?.get(1)
            ?: NAME_AFTER_RECIBISTE.find(s)?.groupValues?.get(1)
            ?: return null
        // Drop leading words like "Yape!" or "Confirmación de Pago" that sit before the name.
        val words = raw.trim().split(Regex("""\s+"""))
            .dropWhile { ReceiptParser.fold(it).trimEnd('!', '.', ':') in NOT_NAMES }
        // A word ending in "." (other than an initial like "J.") ends the sentence, and the name.
        val end = words.indexOfFirst { it.endsWith('.') && it.length > 2 }
        val name = if (end >= 0) words.take(end + 1) else words
        return name.joinToString(" ").trim().trimEnd('.', ',').ifEmpty { null }
    }
}
