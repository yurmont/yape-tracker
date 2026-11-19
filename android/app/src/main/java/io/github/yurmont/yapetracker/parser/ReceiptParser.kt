package io.github.yurmont.yapetracker.parser

import java.text.Normalizer

/** Data read from a shared "¡Yapeaste!" receipt. Any field may be null when OCR missed it. */
data class Receipt(
    val isYapeaste: Boolean,
    /** Amount with a dot decimal separator and no thousands separator, e.g. "1250.50". */
    val amount: String?,
    val recipient: String?,
    /** e.g. "19 nov. 2026" */
    val date: String?,
    /** e.g. "01:31 p. m." */
    val time: String?,
    val operation: String?,
    /** Three digits, e.g. "358". */
    val securityCode: String?,
) {
    val dateTime: String?
        get() = listOfNotNull(date, time).joinToString(" ").ifEmpty { null }
}

/**
 * Parses OCR text of a Yape receipt. Pure Kotlin, no Android dependencies.
 *
 * The receipt shows labels and values in two columns, so OCR may return every label first and then
 * every value. Nothing here relies on the order between those columns.
 */
object ReceiptParser {

    private const val NUMBER = """\d{1,3}(?:,\d{3})+\.\d{2}|\d{1,6}[.,]\d{2}"""
    private val AMOUNT_AFTER_SOL = Regex("""S\s*/\s*\.?\s*($NUMBER)(?!\d)""", RegexOption.IGNORE_CASE)
    private val AMOUNT_TOKEN = Regex("""^(?:$NUMBER)$""")
    private val SOL_ONLY = Regex("""^s\s*/\s*\.?$""")

    private val DATE = Regex("""(\d{1,2})\s+([a-z]{3})\.?\s+(\d{4})""")
    private val TIME = Regex("""(\d{1,2}:\d{2})\s*([ap])\.?\s*m\b\.?""")

    private val OP_INLINE = Regex("""operacion\D{0,20}?(\d{6,14})(?!\d)""")
    private val STANDALONE_DIGITS = Regex("""(?<![\d*])\d{7,12}(?!\d)""")

    private val CODE_INLINE = Regex("""seguridad\D*?(\d)\s*(\d)\s*(\d)(?!\d)""")
    private val CODE_LINE = Regex("""^(\d)\s*(\d)\s*(\d)$""")
    private val SINGLE_DIGIT = Regex("""^\d$""")

    private val LABELS = listOf(
        "yapeaste", "compartir", "codigo de seguridad", "datos de la transaccion",
        "celular", "destino", "operacion", "mas en yape",
    )

    fun parse(text: String): Receipt = parse(text.lines())

    fun parse(rawLines: List<String>): Receipt {
        val lines = rawLines
            .map { Normalizer.normalize(it, Normalizer.Form.NFC).replace(Regex("""\s+"""), " ").trim() }
            .filter { it.isNotEmpty() }
        val folded = lines.map(::fold)

        val isYapeaste = folded.any { "yapeaste" in it }
        val (amount, amountIdx) = findAmount(lines)
        val recipient = findRecipient(lines, folded, amountIdx)

        var date: String? = null
        var time: String? = null
        for (f in folded) {
            if (date == null) DATE.find(f)?.let { m ->
                date = "${m.groupValues[1]} ${m.groupValues[2]}. ${m.groupValues[3]}"
            }
            if (time == null) TIME.find(f)?.let { m ->
                time = "${m.groupValues[1]} ${m.groupValues[2]}. m."
            }
        }

        return Receipt(
            isYapeaste = isYapeaste,
            amount = amount,
            recipient = recipient,
            date = date,
            time = time,
            operation = findOperation(folded),
            securityCode = findSecurityCode(folded),
        )
    }

    /** Lowercase without accents, so "OPERACIÓN" and an OCR'd "operacion" compare equal. */
    internal fun fold(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("""\p{Mn}+"""), "").lowercase()

    /** "1,250.50" → "1250.50"; "12,50" (comma read as decimal) → "12.50". */
    internal fun normalizeAmount(raw: String): String =
        if ('.' in raw) raw.replace(",", "") else raw.replace(',', '.')

    private fun findAmount(lines: List<String>): Pair<String?, Int> {
        // "S/ 0.40" on one line.
        lines.forEachIndexed { i, line ->
            AMOUNT_AFTER_SOL.find(line)?.let { return normalizeAmount(it.groupValues[1]) to i }
        }
        // "S/" and "0.40" as separate, adjacent OCR blocks.
        AMOUNT_AFTER_SOL.find(lines.joinToString(" "))?.let { m ->
            val raw = m.groupValues[1]
            val idx = lines.indexOfFirst { line -> line.split(' ').any { it == raw } }
            return normalizeAmount(raw) to idx
        }
        // First standalone token that looks like an amount.
        lines.forEachIndexed { i, line ->
            line.split(' ').firstOrNull { AMOUNT_TOKEN.matches(it) }?.let { return normalizeAmount(it) to i }
        }
        return null to -1
    }

    private fun isNameCandidate(line: String, f: String): Boolean {
        if (!line.any { it.isLetter() }) return false
        if (SOL_ONLY.matches(f) || "s/" in f.replace(" ", "")) return false
        if (f.filter { it.isLetter() } == "yape") return false
        if (LABELS.any { it in f }) return false
        if (DATE.containsMatchIn(f) || TIME.containsMatchIn(f)) return false
        return true
    }

    private fun findRecipient(lines: List<String>, folded: List<String>, amountIdx: Int): String? {
        val start = if (amountIdx >= 0) amountIdx + 1 else 0
        val after = (start until lines.size).filter { isNameCandidate(lines[it], folded[it]) }
        val all = lines.indices.filter { isNameCandidate(lines[it], folded[it]) }
        // Yape masks the recipient's surname with "*", which makes it the strongest signal.
        val idx = after.firstOrNull { lines[it].endsWith("*") }
            ?: all.firstOrNull { lines[it].endsWith("*") }
            ?: after.firstOrNull()
        return idx?.let { lines[it] }
    }

    private fun findOperation(folded: List<String>): String? {
        folded.forEach { f -> OP_INLINE.find(f)?.let { return it.groupValues[1] } }
        return folded
            .flatMap { f -> STANDALONE_DIGITS.findAll(f).map { it.value }.toList() }
            .maxByOrNull { it.length }
    }

    private fun findSecurityCode(folded: List<String>): String? {
        if (folded.none { "codigo de seguridad" in it }) return null
        folded.forEach { f ->
            CODE_INLINE.find(f)?.let { m -> return m.groupValues.drop(1).joinToString("") }
        }
        folded.forEach { f ->
            CODE_LINE.find(f)?.let { m -> return m.groupValues.drop(1).joinToString("") }
        }
        for (i in 0..folded.size - 3) {
            if ((i..i + 2).all { SINGLE_DIGIT.matches(folded[it]) }) {
                return folded[i] + folded[i + 1] + folded[i + 2]
            }
        }
        return null
    }
}
