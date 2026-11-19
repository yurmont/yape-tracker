package io.github.yurmont.yapetracker.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class EntryFormatterTest {

    @Test fun fullEntry() {
        assertEquals(
            "Yapeaste S/ 0.40 a Ronald Yur* | 19 nov. 2026 01:31 p. m. | op 19087981 | almuerzo",
            EntryFormatter.expense("0.40", "Ronald Yur*", "19 nov. 2026 01:31 p. m.", "19087981", "almuerzo"),
        )
    }

    @Test fun withoutConcepto() {
        assertEquals(
            "Yapeaste S/ 0.40 a Ronald Yur* | 19 nov. 2026 01:31 p. m. | op 19087981",
            EntryFormatter.expense("0.40", "Ronald Yur*", "19 nov. 2026 01:31 p. m.", "19087981", " "),
        )
    }

    @Test fun onlyAmount() {
        assertEquals("Yapeaste S/ 5.00", EntryFormatter.expense("5.00", null, null, "", null))
    }

    @Test fun pipeInsideAFieldIsReplaced() {
        assertEquals(
            "Yapeaste S/ 5.00 a Ana Q* | op 1234567 | pan / leche",
            EntryFormatter.expense("5.00", "Ana Q*", null, "1234567", "pan | leche"),
        )
    }
}
