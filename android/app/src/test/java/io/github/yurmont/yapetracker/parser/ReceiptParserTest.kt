package io.github.yurmont.yapetracker.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptParserTest {

    /** The receipt from docs/APK-HANDOFF.md, read row by row. */
    private val rowOrder = """
        yape
        JOSÉ QUIÑONES
        ¡Yapeaste!
        Compartir
        S/ 0.40
        Ronald Yur*
        19 nov. 2026 | 01:31 p. m.
        CÓDIGO DE SEGURIDAD 3 5 8
        DATOS DE LA TRANSACCIÓN
        Nro. de celular *** *** 691
        Destino Yape
        Nro. de operación 19087981
        Más en Yape
        Paga tus servicios y recarga tu celular
    """.trimIndent()

    /** Same receipt, but OCR returned the label column first and then the value column. */
    private val columnOrder = """
        yape
        JOSÉ QUIÑONES
        ¡Yapeaste!
        Compartir
        S/
        0.40
        Ronald Yur*
        19 nov. 2026 | 01:31 p. m.
        CÓDIGO DE SEGURIDAD
        DATOS DE LA TRANSACCIÓN
        Nro. de celular
        Destino
        Nro. de operación
        3
        5
        8
        *** *** 691
        Yape
        19087981
        Más en Yape
    """.trimIndent()

    private fun assertReceipt(r: Receipt) {
        assertTrue(r.isYapeaste)
        assertEquals("0.40", r.amount)
        assertEquals("Ronald Yur*", r.recipient)
        assertEquals("19 nov. 2026", r.date)
        assertEquals("01:31 p. m.", r.time)
        assertEquals("19 nov. 2026 01:31 p. m.", r.dateTime)
        assertEquals("19087981", r.operation)
        assertEquals("358", r.securityCode)
    }

    @Test fun rowOrder() = assertReceipt(ReceiptParser.parse(rowOrder))

    @Test fun labelsAndValuesInSeparateColumns() = assertReceipt(ReceiptParser.parse(columnOrder))

    @Test fun valuesBeforeLabels() {
        val text = """
            S/
            0.40
            Ronald Yur*
            19087981
            *** *** 691
            Yape
            ¡Yapeaste!
            19 nov. 2026
            01:31 p. m.
            Nro. de operación
            Destino
            Nro. de celular
            CÓDIGO DE SEGURIDAD 358
        """.trimIndent()
        assertReceipt(ReceiptParser.parse(text))
    }

    @Test fun ocrWithoutAccentsOrInvertedExclamation() {
        val text = """
            Yapeaste!
            S/ 25.00
            Maria Lop*
            3 ene 2027 | 9:05 a.m.
            CODIGO DE SEGURIDAD 1 2 3
            Nro. de operacion 00123456
        """.trimIndent()
        val r = ReceiptParser.parse(text)
        assertTrue(r.isYapeaste)
        assertEquals("25.00", r.amount)
        assertEquals("Maria Lop*", r.recipient)
        assertEquals("3 ene. 2027", r.date)
        assertEquals("9:05 a. m.", r.time)
        assertEquals("00123456", r.operation)
        assertEquals("123", r.securityCode)
    }

    @Test fun thousandsSeparator() {
        assertEquals("1250.50", ReceiptParser.parse("¡Yapeaste!\nS/ 1,250.50\nAna Q*").amount)
    }

    @Test fun commaReadAsDecimal() {
        assertEquals("12.50", ReceiptParser.parse("¡Yapeaste!\nS/ 12,50\nAna Q*").amount)
    }

    @Test fun amountWithoutCurrencyFallsBackToToken() {
        val r = ReceiptParser.parse("¡Yapeaste!\n7.90\nLuis P*\nNro. de operación 55512345")
        assertEquals("7.90", r.amount)
        assertEquals("Luis P*", r.recipient)
    }

    @Test fun recipientWithoutAsteriskIsFirstNameLineAfterAmount() {
        val r = ReceiptParser.parse("¡Yapeaste!\nS/ 3.00\nBodega San Juan\n19 nov. 2026 | 01:31 p. m.")
        assertEquals("Bodega San Juan", r.recipient)
    }

    @Test fun operationFallsBackToLongestDigitRun() {
        val r = ReceiptParser.parse("¡Yapeaste!\nS/ 3.00\nAna Q*\n*** *** 691\n2026\n987654321")
        assertEquals("987654321", r.operation)
    }

    @Test fun notAReceipt() {
        val r = ReceiptParser.parse("WhatsApp\nHola, ¿cómo estás?")
        assertFalse(r.isYapeaste)
        assertNull(r.amount)
        assertNull(r.operation)
        assertNull(r.securityCode)
    }

    @Test fun emptyText() {
        val r = ReceiptParser.parse("")
        assertFalse(r.isYapeaste)
        assertNull(r.recipient)
        assertNull(r.dateTime)
    }
}
