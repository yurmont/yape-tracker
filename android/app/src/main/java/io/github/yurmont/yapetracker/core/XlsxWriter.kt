package io.github.yurmont.yapetracker.core

import java.io.OutputStream
import java.math.BigDecimal
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a one-sheet .xlsx file (Office Open XML) that Excel, Google Sheets and LibreOffice open.
 * It covers only what this app needs: text, amounts, date-times, a bold header and SUM formulas.
 */
object XlsxWriter {

    sealed interface Cell
    data class Text(val value: String, val bold: Boolean = false) : Cell
    /** Shown with two decimals and thousands separators. */
    data class Amount(val cents: Long) : Cell
    /** Excel serial date (days since 1899-12-30, fraction = time of day). */
    data class DateTime(val serial: Double) : Cell
    data class Formula(val expression: String, val boldAmount: Boolean = true) : Cell

    data class Sheet(
        val name: String,
        val columnWidths: List<Double>,
        val rows: List<List<Cell?>>,
    )

    // Indexes into cellXfs in styles.xml.
    private const val STYLE_BOLD = 1
    private const val STYLE_AMOUNT = 2
    private const val STYLE_DATETIME = 3
    private const val STYLE_BOLD_AMOUNT = 4

    fun write(sheet: Sheet, out: OutputStream) {
        val zip = ZipOutputStream(out)
        fun entry(name: String, content: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        entry("[Content_Types].xml", CONTENT_TYPES)
        entry("_rels/.rels", ROOT_RELS)
        entry("xl/workbook.xml", workbook(sheetName(sheet.name)))
        entry("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
        entry("xl/styles.xml", STYLES)
        entry("xl/worksheets/sheet1.xml", worksheet(sheet))
        zip.finish()
        zip.flush()
    }

    /** 0 → "A", 25 → "Z", 26 → "AA" */
    fun columnName(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append('A' + rem)
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    fun escape(s: String): String = buildString(s.length) {
        for (ch in s) {
            when {
                ch == '&' -> append("&amp;")
                ch == '<' -> append("&lt;")
                ch == '>' -> append("&gt;")
                ch == '"' -> append("&quot;")
                // XML 1.0 forbids most control characters; drop them.
                ch < ' ' && ch != '\t' && ch != '\n' && ch != '\r' -> Unit
                else -> append(ch)
            }
        }
    }

    /** Excel sheet names: at most 31 characters, none of []:*?/\ */
    private fun sheetName(name: String): String =
        name.replace(Regex("""[\[\]:*?/\\]"""), " ").trim().take(31).ifEmpty { "Hoja1" }

    private fun worksheet(sheet: Sheet): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        append("""<sheetViews><sheetView workbookViewId="0">""")
        append("""<pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>""")
        append("""</sheetView></sheetViews>""")
        if (sheet.columnWidths.isNotEmpty()) {
            append("<cols>")
            sheet.columnWidths.forEachIndexed { i, w ->
                append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""")
            }
            append("</cols>")
        }
        append("<sheetData>")
        sheet.rows.forEachIndexed { r, row ->
            val rowNum = r + 1
            append("""<row r="$rowNum">""")
            row.forEachIndexed { c, cell ->
                if (cell != null) append(cellXml("${columnName(c)}$rowNum", cell))
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun cellXml(ref: String, cell: Cell): String = when (cell) {
        is Text -> {
            val style = if (cell.bold) """ s="$STYLE_BOLD"""" else ""
            """<c r="$ref" t="inlineStr"$style><is><t xml:space="preserve">${escape(cell.value)}</t></is></c>"""
        }
        is Amount ->
            """<c r="$ref" s="$STYLE_AMOUNT"><v>${BigDecimal.valueOf(cell.cents, 2).toPlainString()}</v></c>"""
        is DateTime ->
            """<c r="$ref" s="$STYLE_DATETIME"><v>${BigDecimal.valueOf(cell.serial).toPlainString()}</v></c>"""
        is Formula -> {
            val style = if (cell.boldAmount) STYLE_BOLD_AMOUNT else STYLE_AMOUNT
            """<c r="$ref" s="$style"><f>${escape(cell.expression)}</f></c>"""
        }
    }

    private fun workbook(name: String) =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """ +
            """xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""" +
            """<sheets><sheet name="${escape(name)}" sheetId="1" r:id="rId1"/></sheets></workbook>"""

    private const val CONTENT_TYPES =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
            """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
            """<Default Extension="xml" ContentType="application/xml"/>""" +
            """<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""" +
            """<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" +
            """<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""" +
            """</Types>"""

    private const val ROOT_RELS =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
            """</Relationships>"""

    private const val WORKBOOK_RELS =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>""" +
            """<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""" +
            """</Relationships>"""

    private const val STYLES =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
            """<numFmts count="1"><numFmt numFmtId="164" formatCode="dd/mm/yyyy hh:mm"/></numFmts>""" +
            """<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font>""" +
            """<font><b/><sz val="11"/><name val="Calibri"/></font></fonts>""" +
            """<fills count="2"><fill><patternFill patternType="none"/></fill>""" +
            """<fill><patternFill patternType="gray125"/></fill></fills>""" +
            """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
            """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
            """<cellXfs count="5">""" +
            """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
            """<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>""" +
            """<xf numFmtId="4" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
            """<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
            """<xf numFmtId="4" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1" applyNumberFormat="1"/>""" +
            """</cellXfs>""" +
            """<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>""" +
            """</styleSheet>"""
}
