package io.github.yurmont.yapetracker.core

import io.github.yurmont.yapetracker.core.XlsxWriter.Amount
import io.github.yurmont.yapetracker.core.XlsxWriter.DateTime
import io.github.yurmont.yapetracker.core.XlsxWriter.Formula
import io.github.yurmont.yapetracker.core.XlsxWriter.Text
import java.util.TimeZone

/** Turns stored movements into the exported spreadsheet: one row each plus a total row. */
object MovementSheet {

    val HEADER = listOf(
        "Registrado", "Tipo", "Monto", "Importe", "Persona",
        "Fecha en Yape", "Nro. de operación", "Concepto", "Texto",
    )
    private val WIDTHS = listOf(17.0, 9.0, 11.0, 11.0, 24.0, 24.0, 17.0, 24.0, 60.0)

    /** `movements` in the order they should appear (the app passes oldest first). */
    fun build(sheetName: String, movements: List<Movement>, zone: TimeZone): XlsxWriter.Sheet {
        val rows = mutableListOf<List<XlsxWriter.Cell?>>()
        rows += HEADER.map { Text(it, bold = true) }
        movements.forEach { m ->
            rows += listOf(
                DateTime(excelSerial(m.createdAt, zone)),
                Text(m.type.label),
                Amount(m.amountCents),
                Amount(m.signedCents),
                m.person?.let(::Text),
                m.yapeDate?.let(::Text),
                m.operation?.let(::Text),
                m.concepto?.let(::Text),
                Text(m.rawText),
            )
        }
        if (movements.isNotEmpty()) {
            val last = movements.size + 1
            rows += listOf(
                Text("Total", bold = true), null,
                Formula("SUM(C2:C$last)"), Formula("SUM(D2:D$last)"),
            )
        }
        return XlsxWriter.Sheet(sheetName, WIDTHS, rows)
    }

    /** Local date-time as an Excel serial number (days since 1899-12-30). */
    fun excelSerial(epochMillis: Long, zone: TimeZone): Double {
        val local = epochMillis + zone.getOffset(epochMillis)
        return local / 86_400_000.0 + 25_569.0
    }
}
