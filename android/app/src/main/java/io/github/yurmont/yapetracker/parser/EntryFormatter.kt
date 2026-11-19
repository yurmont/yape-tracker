package io.github.yurmont.yapetracker.parser

/**
 * Builds the one-line summary stored with each expense (the "Texto" column in the Excel export):
 *
 *     Yapeaste S/ 0.40 a Ronald Yur* | 19 nov. 2026 01:31 p. m. | op 19087981 | almuerzo
 */
object EntryFormatter {

    fun expense(
        amount: String,
        recipient: String?,
        dateTime: String?,
        operation: String?,
        concepto: String?,
    ): String {
        val head = buildString {
            append("Yapeaste S/ ").append(clean(amount))
            recipient?.let(::clean)?.takeIf { it.isNotEmpty() }?.let { append(" a ").append(it) }
        }
        val rest = listOf(
            dateTime?.let(::clean),
            operation?.let(::clean)?.takeIf { it.isNotEmpty() }?.let { "op $it" },
            concepto?.let(::clean),
        )
        return (listOf(head) + rest).filter { !it.isNullOrEmpty() }.joinToString(" | ")
    }

    /** "|" separates fields in the summary, so it can't appear inside one. */
    private fun clean(s: String): String = s.replace('|', '/').replace(Regex("""\s+"""), " ").trim()
}
