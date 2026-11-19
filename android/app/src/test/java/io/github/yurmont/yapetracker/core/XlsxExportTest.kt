package io.github.yurmont.yapetracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.TimeZone
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class XlsxExportTest {

    private val lima = TimeZone.getTimeZone("America/Lima")

    private val movements = listOf(
        Movement(
            id = 1, type = MovementType.GASTO, amountCents = 40, person = "Ronald Yur*",
            yapeDate = "19 nov. 2026 01:31 p. m.", operation = "19087981", concepto = "pan & café <2>",
            rawText = "Yapeaste S/ 0.40 a Ronald Yur* | 19 nov. 2026 01:31 p. m. | op 19087981",
            // 2026-11-19 13:31 in Lima (UTC-5)
            createdAt = 1_795_113_060_000L,
        ),
        Movement(
            id = 2, type = MovementType.INGRESO, amountCents = 125050, person = "Ana",
            yapeDate = null, operation = null, concepto = null,
            rawText = "Yape! Ana te envió un pago por S/ 1,250.50", createdAt = 1_795_116_000_000L,
        ),
    )

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val files = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                files[e.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        return files
    }

    private fun export(): Map<String, String> {
        val out = ByteArrayOutputStream()
        XlsxWriter.write(MovementSheet.build("Movimientos", movements, lima), out)
        return unzip(out.toByteArray())
    }

    @Test fun packageHasAllParts() {
        assertEquals(
            setOf(
                "[Content_Types].xml", "_rels/.rels", "xl/workbook.xml",
                "xl/_rels/workbook.xml.rels", "xl/styles.xml", "xl/worksheets/sheet1.xml",
            ),
            export().keys,
        )
    }

    @Test fun everyPartIsWellFormedXml() {
        val builder = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
        export().forEach { (name, xml) ->
            builder.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
                ?: error("$name did not parse")
        }
    }

    @Test fun sheetContents() {
        val xml = export().getValue("xl/worksheets/sheet1.xml")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        val cells = doc.getElementsByTagName("c")
        val byRef = (0 until cells.length).associate { i ->
            val c = cells.item(i) as Element
            c.getAttribute("r") to c.textContent
        }
        assertEquals("Registrado", byRef["A1"])
        assertEquals("Nro. de operación", byRef["G1"])
        assertEquals("Gasto", byRef["B2"])
        assertEquals("0.40", byRef["C2"])
        assertEquals("-0.40", byRef["D2"])
        assertEquals("Ronald Yur*", byRef["E2"])
        assertEquals("pan & café <2>", byRef["H2"])
        assertEquals("Ingreso", byRef["B3"])
        assertEquals("1250.50", byRef["D3"])
        assertEquals("Total", byRef["A4"])
        assertTrue(xml.contains("<f>SUM(D2:D3)</f>"))
    }

    @Test fun dateIsLocalExcelSerial() {
        // 2026-11-19 13:31 local → 46345 days + 13:31 as a fraction of a day.
        val serial = MovementSheet.excelSerial(1_795_113_060_000L, lima)
        assertEquals(46345.0 + (13 * 60 + 31) / 1440.0, serial, 1e-9)
    }

    @Test fun emptyExportHasOnlyHeader() {
        val out = ByteArrayOutputStream()
        XlsxWriter.write(MovementSheet.build("Gastos", emptyList(), lima), out)
        val xml = unzip(out.toByteArray()).getValue("xl/worksheets/sheet1.xml")
        assertTrue(xml.contains("""<row r="1">"""))
        assertTrue(!xml.contains("""<row r="2">"""))
    }

    @Test fun columnNames() {
        assertEquals("A", XlsxWriter.columnName(0))
        assertEquals("Z", XlsxWriter.columnName(25))
        assertEquals("AA", XlsxWriter.columnName(26))
    }
}
